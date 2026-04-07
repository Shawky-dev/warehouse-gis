package com.warehouse.warehouse_platform.tenant.gis.service;

import com.warehouse.warehouse_platform.tenant.gis.GisException;
import com.warehouse.warehouse_platform.tenant.gis.GeoServerProvisioningException;
import com.warehouse.warehouse_platform.tenant.gis.model.PublishStatus;
import com.warehouse.warehouse_platform.tenant.gis.model.StaticHeatmap;
import com.warehouse.warehouse_platform.tenant.gis.repository.StaticHeatmapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class StaticHeatmapService {

    private static final Logger log = LoggerFactory.getLogger(StaticHeatmapService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/tiff", "image/geotiff", "image/x-tiff", "image/x-geotiff", "application/octet-stream");

    private final StaticHeatmapRepository repo;
    private final GeoServerProvisioningService geoServer;
    private final TransactionTemplate txTemplate;
    private final TransactionTemplate requiresNewTxTemplate;

    public StaticHeatmapService(
            StaticHeatmapRepository repo,
            GeoServerProvisioningService geoServer,
            PlatformTransactionManager txManager) {
        this.repo = repo;
        this.geoServer = geoServer;
        this.txTemplate = new TransactionTemplate(txManager);
        this.requiresNewTxTemplate = new TransactionTemplate(txManager);
        this.requiresNewTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ─── List ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StaticHeatmap> listActive() {
        return repo.findAllByPublishStatusOrderByCreatedAtDesc(PublishStatus.ACTIVE);
    }

    // ─── Upload ───────────────────────────────────────────────────────────────

    /**
     * Validates, publishes to GeoServer, and persists metadata for a new static
     * heatmap.
     * The first active heatmap in a tenant schema is automatically set as default.
     */
    public StaticHeatmap upload(String tenantSlug, String name, MultipartFile file, String uploadedBy) {
        validateFile(file);

        UUID id = UUID.randomUUID();
        String id8 = id.toString().replace("-", "").substring(0, 8);
        String storeName = "heatmap_static_" + id8;

        byte[] tiffBytes;
        try {
            tiffBytes = file.getBytes();
        } catch (IOException e) {
            throw GisException.badRequest("Failed to read uploaded file: " + e.getMessage());
        }

        // GeoServer publish happens OUTSIDE the DB transaction so that a DB failure
        // can still attempt GeoServer cleanup as compensation.
        geoServer.ensureTenantWorkspace(tenantSlug);
        geoServer.uploadGeoTiffCoverageStore(tenantSlug, storeName, tiffBytes);

        // Persist metadata in a DB transaction.
        StaticHeatmap persisted;
        try {
            persisted = txTemplate.execute(tx -> {
                boolean isFirst = repo.countByPublishStatus(PublishStatus.ACTIVE) == 0;
                StaticHeatmap heatmap = StaticHeatmap.builder()
                        .id(id)
                        .name(name)
                        .sourceFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : storeName)
                        .contentType(resolveContentType(file))
                        .geoserverCoverageStore(storeName)
                        .geoserverLayerName(storeName)
                        .publishStatus(PublishStatus.ACTIVE)
                        .isDefault(isFirst)
                        .uploadedBy(uploadedBy)
                        .build();
                return repo.save(heatmap);
            });
        } catch (Exception dbEx) {
            // DB failed after GeoServer succeeded → attempt GeoServer cleanup.
            log.error("DB persist failed for heatmap id={} in tenant={} after GeoServer upload. "
                    + "Attempting GeoServer cleanup. store={}", id, tenantSlug, storeName, dbEx);
            try {
                geoServer.deleteRasterCoverageStore(tenantSlug, storeName);
            } catch (GeoServerProvisioningException cleanupEx) {
                log.error("CRITICAL: GeoServer cleanup after DB failure also failed for heatmap id={} "
                        + "in tenant={}. Manual cleanup required. store={}", id, tenantSlug, storeName, cleanupEx);
            }
            throw GisException.badRequest("Heatmap upload failed: " + dbEx.getMessage());
        }
        return persisted;
    }

    // ─── Set default ──────────────────────────────────────────────────────────

    @Transactional
    public StaticHeatmap setDefault(UUID id) {
        StaticHeatmap heatmap = repo.findById(id)
                .filter(h -> h.getPublishStatus() == PublishStatus.ACTIVE)
                .orElseThrow(() -> GisException.notFound("Heatmap not found or not active"));

        // Bulk update clears all ACTIVE defaults; clearAutomatically = true evicts
        // cache.
        repo.clearAllDefaults(PublishStatus.ACTIVE);
        heatmap.setDefault(true);
        // After cache eviction, save merges the entity with is_default = true.
        return repo.save(heatmap);
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    /**
     * Deletes a static heatmap: GeoServer store removed first, then DB row deleted.
     * If GeoServer deletion succeeds but the DB deletion fails, the row is marked
     * ORPHANED
     * in a compensating transaction so it is excluded from all normal queries.
     */
    public void delete(String tenantSlug, UUID id) {
        StaticHeatmap heatmap = repo.findById(id)
                .filter(h -> h.getPublishStatus() == PublishStatus.ACTIVE)
                .orElseThrow(() -> GisException.notFound("Heatmap not found or not active"));

        UUID promotionTargetId = null;
        if (heatmap.isDefault()) {
            promotionTargetId = repo
                    .findTop1ByPublishStatusAndIdNotOrderByCreatedAtDesc(PublishStatus.ACTIVE, id)
                    .map(StaticHeatmap::getId)
                    .orElse(null);
        }
        final UUID finalPromotionTargetId = promotionTargetId;

        // Step 1: GeoServer delete (outside any transaction).
        geoServer.deleteRasterCoverageStore(tenantSlug, heatmap.getGeoserverCoverageStore());

        // Step 2: DB delete inside a transaction.
        try {
            txTemplate.executeWithoutResult(tx -> {
                repo.deleteById(id);
                if (finalPromotionTargetId != null) {
                    repo.findById(finalPromotionTargetId).ifPresent(replacement -> {
                        replacement.setDefault(true);
                        repo.save(replacement);
                    });
                }
            });
        } catch (Exception dbEx) {
            // GeoServer deletion already committed → mark row ORPHANED so it cannot be
            // used.
            log.error("DB delete failed for heatmap id={} in tenant={} after GeoServer deletion succeeded. "
                    + "Attempting to mark row as ORPHANED. store={}, layer={}",
                    id, tenantSlug, heatmap.getGeoserverCoverageStore(), heatmap.getGeoserverLayerName(), dbEx);
            try {
                requiresNewTxTemplate.executeWithoutResult(tx -> repo.findById(id).ifPresent(h -> {
                    h.setPublishStatus(PublishStatus.ORPHANED);
                    h.setDefault(false);
                    repo.save(h);
                }));
            } catch (Exception compEx) {
                log.error("CRITICAL: Orphan compensation also failed for heatmap id={} in tenant={}. "
                        + "Manual cleanup required. store={}, layer={}",
                        id, tenantSlug, heatmap.getGeoserverCoverageStore(), heatmap.getGeoserverLayerName(),
                        compEx);
            }
            throw GisException.conflict("Heatmap deletion failed; metadata has been marked as orphaned");
        }
    }

    // ─── Validation helpers ───────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw GisException.badRequest("Uploaded file is empty");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (!originalName.endsWith(".tif") && !originalName.endsWith(".tiff")) {
            throw GisException.badRequest("Only GeoTIFF files (.tif, .tiff) are accepted");
        }
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw GisException.badRequest(
                    "Unsupported content type '%s'. Expected a TIFF variant.".formatted(contentType));
        }
    }

    private String resolveContentType(MultipartFile file) {
        String ct = file.getContentType();
        return (ct != null && !ct.isBlank()) ? ct : "image/tiff";
    }
}
