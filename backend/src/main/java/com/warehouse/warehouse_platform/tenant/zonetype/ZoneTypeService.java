package com.warehouse.warehouse_platform.tenant.zonetype;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.category.ProductCategoryRepository;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ZoneTypeService {

    private final ZoneTypeRepository zoneTypeRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final GisZoneRepository gisZoneRepository;
    private final TenantAuditService tenantAuditService;

    public ZoneTypeService(
            ZoneTypeRepository zoneTypeRepository,
            ProductCategoryRepository productCategoryRepository,
            GisZoneRepository gisZoneRepository,
            TenantAuditService tenantAuditService) {
        this.zoneTypeRepository = zoneTypeRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.gisZoneRepository = gisZoneRepository;
        this.tenantAuditService = tenantAuditService;
    }

    // ── read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ZoneTypeResult> listAll() {
        return zoneTypeRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(ZoneType::getCode))
                .map(this::toResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public ZoneTypeResult get(UUID id) {
        return toResult(load(id));
    }

    // ── write ─────────────────────────────────────────────────────────────────

    @Transactional
    public ZoneTypeResult create(String code, String displayName) {
        String normalized = normalizeCode(code);
        String normalizedDisplay = normalizeDisplayName(displayName);
        assertCodeUnique(normalized, null);

        ZoneType entity = ZoneType.builder()
                .code(normalized)
                .displayName(normalizedDisplay)
                .isActive(true)
                .build();

        ZoneType saved = zoneTypeRepository.save(entity);
        ZoneTypeResult result = toResult(saved);
        tenantAuditService.record("ZONE_TYPE_CREATE", "ZONE_TYPE", saved.getId().toString(), null, result);
        return result;
    }

    @Transactional
    public ZoneTypeResult update(UUID id, String code, String displayName) {
        ZoneType existing = load(id);
        ZoneTypeResult before = toResult(existing);

        String normalized = normalizeCode(code);
        String normalizedDisplay = normalizeDisplayName(displayName);
        assertCodeUnique(normalized, id);

        existing.setCode(normalized);
        existing.setDisplayName(normalizedDisplay);

        ZoneType saved = zoneTypeRepository.save(existing);
        ZoneTypeResult after = toResult(saved);
        tenantAuditService.record("ZONE_TYPE_UPDATE", "ZONE_TYPE", id.toString(), before, after);
        return after;
    }

    @Transactional
    public void deactivate(UUID id) {
        ZoneType entity = load(id);
        if (!Boolean.TRUE.equals(entity.getIsActive()))
            return;

        ZoneTypeResult before = toResult(entity);
        entity.setIsActive(false);
        entity.setDeactivatedAt(Instant.now());
        zoneTypeRepository.save(entity);
        tenantAuditService.record("ZONE_TYPE_DEACTIVATE", "ZONE_TYPE", id.toString(), before, toResult(entity));
    }

    @Transactional
    public void reactivate(UUID id) {
        ZoneType entity = load(id);
        if (Boolean.TRUE.equals(entity.getIsActive()))
            return;

        ZoneTypeResult before = toResult(entity);
        entity.setIsActive(true);
        entity.setDeactivatedAt(null);
        zoneTypeRepository.save(entity);
        tenantAuditService.record("ZONE_TYPE_REACTIVATE", "ZONE_TYPE", id.toString(), before, toResult(entity));
    }

    @Transactional
    public void delete(UUID id) {
        ZoneType entity = load(id);
        assertNotReferencedByCategories(id);
        assertNotReferencedByZones(id);
        zoneTypeRepository.delete(entity);
        tenantAuditService.record("ZONE_TYPE_DELETE", "ZONE_TYPE", id.toString(), toResult(entity), null);
    }

    // ── package-visible helpers ───────────────────────────────────────────────

    ZoneType load(UUID id) {
        return zoneTypeRepository.findById(id)
                .orElseThrow(() -> ZoneTypeException.notFound("Zone type not found: " + id));
    }

    ZoneTypeResult toResult(ZoneType e) {
        return new ZoneTypeResult(
                e.getId(),
                e.getCode(),
                e.getDisplayName(),
                Boolean.TRUE.equals(e.getIsActive()),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getDeactivatedAt());
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void assertCodeUnique(String code, UUID excludeId) {
        if (excludeId == null) {
            if (zoneTypeRepository.existsByCodeIgnoreCase(code)) {
                throw ZoneTypeException.conflict("Zone type code already exists: " + code);
            }
        } else {
            if (zoneTypeRepository.existsByIdNotAndCodeIgnoreCase(excludeId, code)) {
                throw ZoneTypeException.conflict("Zone type code already exists: " + code);
            }
        }
    }

    private void assertNotReferencedByCategories(UUID zoneTypeId) {
        long count = productCategoryRepository.countByRequiredZoneType_Id(zoneTypeId);
        if (count > 0) {
            throw ZoneTypeException.conflict(
                    "Zone type is referenced by " + count + " product categor(ies) and cannot be deleted");
        }
    }

    private void assertNotReferencedByZones(UUID zoneTypeId) {
        long count = gisZoneRepository.countByZoneType_Id(zoneTypeId);
        if (count > 0) {
            throw ZoneTypeException.conflict(
                    "Zone type is referenced by " + count + " GIS zone(s) and cannot be deleted");
        }
    }

    static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw ZoneTypeException.badRequest("Zone type code must not be blank");
        }
        return code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
    }

    private static String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw ZoneTypeException.badRequest("Zone type display name must not be blank");
        }
        String trimmed = displayName.trim();
        if (trimmed.length() > 120) {
            throw ZoneTypeException.badRequest("Display name must not exceed 120 characters");
        }
        return trimmed;
    }

    // ── result type ───────────────────────────────────────────────────────────

    public record ZoneTypeResult(
            UUID id,
            String code,
            String displayName,
            boolean active,
            java.time.Instant createdAt,
            java.time.Instant updatedAt,
            java.time.Instant deactivatedAt) {
    }
}
