package com.warehouse.warehouse_platform.tenant.hazardtype;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class HazardTypeService {

    private static final String CODE_NONE = "NONE";

    private final HazardTypeRepository hazardTypeRepository;
    private final ProductRepository productRepository;
    private final TenantAuditService tenantAuditService;

    public HazardTypeService(
            HazardTypeRepository hazardTypeRepository,
            ProductRepository productRepository,
            TenantAuditService tenantAuditService) {
        this.hazardTypeRepository = hazardTypeRepository;
        this.productRepository = productRepository;
        this.tenantAuditService = tenantAuditService;
    }

    // ── read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<HazardTypeResult> listAll() {
        return hazardTypeRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(HazardType::getCode))
                .map(this::toResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public HazardTypeResult get(UUID id) {
        return toResult(load(id));
    }

    // ── write ─────────────────────────────────────────────────────────────────

    @Transactional
    public HazardTypeResult create(String code, String displayName) {
        String normalized = normalizeCode(code);
        String normalizedDisplay = normalizeDisplayName(displayName);
        assertCodeUnique(normalized, null);

        HazardType entity = HazardType.builder()
                .code(normalized)
                .displayName(normalizedDisplay)
                .isActive(true)
                .build();

        HazardType saved = hazardTypeRepository.save(entity);
        HazardTypeResult result = toResult(saved);
        tenantAuditService.record("HAZARD_TYPE_CREATE", "HAZARD_TYPE", saved.getId().toString(), null, result);
        return result;
    }

    @Transactional
    public HazardTypeResult update(UUID id, String code, String displayName) {
        HazardType existing = load(id);
        HazardTypeResult before = toResult(existing);

        String normalized = normalizeCode(code);
        String normalizedDisplay = normalizeDisplayName(displayName);
        assertCodeUnique(normalized, id);
        assertNotNoneCode(existing.getCode(), "rename");

        existing.setCode(normalized);
        existing.setDisplayName(normalizedDisplay);

        HazardType saved = hazardTypeRepository.save(existing);
        HazardTypeResult after = toResult(saved);
        tenantAuditService.record("HAZARD_TYPE_UPDATE", "HAZARD_TYPE", id.toString(), before, after);
        return after;
    }

    @Transactional
    public void deactivate(UUID id) {
        HazardType entity = load(id);
        assertCanModifyLifecycle(entity);
        if (!Boolean.TRUE.equals(entity.getIsActive()))
            return;

        HazardTypeResult before = toResult(entity);
        entity.setIsActive(false);
        entity.setDeactivatedAt(Instant.now());
        hazardTypeRepository.save(entity);
        tenantAuditService.record("HAZARD_TYPE_DEACTIVATE", "HAZARD_TYPE", id.toString(), before, toResult(entity));
    }

    @Transactional
    public void reactivate(UUID id) {
        HazardType entity = load(id);
        if (Boolean.TRUE.equals(entity.getIsActive()))
            return;

        HazardTypeResult before = toResult(entity);
        entity.setIsActive(true);
        entity.setDeactivatedAt(null);
        hazardTypeRepository.save(entity);
        tenantAuditService.record("HAZARD_TYPE_REACTIVATE", "HAZARD_TYPE", id.toString(), before, toResult(entity));
    }

    @Transactional
    public void delete(UUID id) {
        HazardType entity = load(id);
        assertCanModifyLifecycle(entity);
        assertNotReferencedByProducts(id);
        hazardTypeRepository.delete(entity);
        tenantAuditService.record("HAZARD_TYPE_DELETE", "HAZARD_TYPE", id.toString(), toResult(entity), null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    HazardType load(UUID id) {
        return hazardTypeRepository.findById(id)
                .orElseThrow(() -> HazardTypeException.notFound("Hazard type not found: " + id));
    }

    HazardTypeResult toResult(HazardType e) {
        return new HazardTypeResult(
                e.getId(),
                e.getCode(),
                e.getDisplayName(),
                Boolean.TRUE.equals(e.getIsActive()),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getDeactivatedAt());
    }

    private void assertCodeUnique(String code, UUID excludeId) {
        if (excludeId == null) {
            if (hazardTypeRepository.existsByCodeIgnoreCase(code)) {
                throw HazardTypeException.conflict("Hazard type code already exists: " + code);
            }
        } else {
            if (hazardTypeRepository.existsByIdNotAndCodeIgnoreCase(excludeId, code)) {
                throw HazardTypeException.conflict("Hazard type code already exists: " + code);
            }
        }
    }

    private void assertCanModifyLifecycle(HazardType entity) {
        if (CODE_NONE.equalsIgnoreCase(entity.getCode())) {
            throw HazardTypeException.forbidden("The NONE hazard type cannot be deactivated or deleted");
        }
    }

    private void assertNotNoneCode(String currentCode, String operation) {
        if (CODE_NONE.equalsIgnoreCase(currentCode)) {
            throw HazardTypeException.forbidden("The NONE hazard type cannot be " + operation + "d");
        }
    }

    private void assertNotReferencedByProducts(UUID hazardTypeId) {
        long count = productRepository.countByHazardType_Id(hazardTypeId);
        if (count > 0) {
            throw HazardTypeException.conflict(
                    "Hazard type is referenced by " + count + " product(s) and cannot be deleted");
        }
    }

    static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw HazardTypeException.badRequest("Hazard type code must not be blank");
        }
        return code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
    }

    private static String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw HazardTypeException.badRequest("Hazard type display name must not be blank");
        }
        String trimmed = displayName.trim();
        if (trimmed.length() > 120) {
            throw HazardTypeException.badRequest("Display name must not exceed 120 characters");
        }
        return trimmed;
    }

    // ── result type ───────────────────────────────────────────────────────────

    public record HazardTypeResult(
            UUID id,
            String code,
            String displayName,
            boolean active,
            java.time.Instant createdAt,
            java.time.Instant updatedAt,
            java.time.Instant deactivatedAt) {
    }
}
