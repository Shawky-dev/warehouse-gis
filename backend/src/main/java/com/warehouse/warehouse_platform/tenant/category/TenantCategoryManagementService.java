package com.warehouse.warehouse_platform.tenant.category;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import com.warehouse.warehouse_platform.tenant.zonetype.ZoneType;
import com.warehouse.warehouse_platform.tenant.zonetype.ZoneTypeException;
import com.warehouse.warehouse_platform.tenant.zonetype.ZoneTypeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TenantCategoryManagementService {

    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final ZoneTypeRepository zoneTypeRepository;
    private final TenantAuditService tenantAuditService;

    public TenantCategoryManagementService(
            ProductCategoryRepository productCategoryRepository,
            ProductRepository productRepository,
            ZoneTypeRepository zoneTypeRepository,
            TenantAuditService tenantAuditService) {
        this.productCategoryRepository = productCategoryRepository;
        this.productRepository = productRepository;
        this.zoneTypeRepository = zoneTypeRepository;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public CategoryPageResult listCategories(int page, int size, String search, Boolean active) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        Page<ProductCategory> result = productCategoryRepository.findAll(buildSpecification(search, active), pageable);
        return new CategoryPageResult(
                result.getContent().stream().map(this::toResult).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CategoryResult getCategory(UUID categoryId) {
        return toResult(loadCategory(categoryId));
    }

    @Transactional
    public CategoryResult createCategory(String name, String code, String displayName, String description, UUID requiredZoneTypeId) {
        String normalizedName = normalizeName(name != null ? name : (displayName != null ? displayName : ""));
        String normalizedCode = normalizeCode(code != null ? code : normalizedName);
        String normalizedDisplay = normalizeDisplayName(displayName != null ? displayName : normalizedName);
        String normalizedDescription = normalizeOptional(description, 500, "description");

        assertCodeUnique(normalizedCode, null);

        ZoneType requiredZoneType = resolveZoneType(requiredZoneTypeId);

        ProductCategory category = ProductCategory.builder()
                .name(normalizedName)
                .code(normalizedCode)
                .displayName(normalizedDisplay)
                .description(normalizedDescription)
                .requiredZoneType(requiredZoneType)
                .active(true)
                .build();

        ProductCategory saved = productCategoryRepository.save(category);
        CategoryResult result = toResult(saved);
        tenantAuditService.record("CATEGORY_CREATE", "CATEGORY", result.id().toString(), null, result);
        return result;
    }

    /** Backward-compatible single-arg create used by legacy callers. */
    @Transactional
    public CategoryResult createCategory(String name, String description) {
        return createCategory(name, null, name, description, null);
    }

    @Transactional
    public CategoryResult updateCategory(UUID categoryId, String name, String code, String displayName, String description, UUID requiredZoneTypeId) {
        ProductCategory existing = loadCategory(categoryId);
        CategoryResult before = toResult(existing);

        String normalizedName = normalizeName(name != null ? name : (displayName != null ? displayName : existing.getName()));
        String normalizedCode = normalizeCode(code != null ? code : normalizedName);
        String normalizedDisplay = normalizeDisplayName(displayName != null ? displayName : normalizedName);
        String normalizedDescription = normalizeOptional(description, 500, "description");

        assertCodeUnique(normalizedCode, categoryId);

        ZoneType requiredZoneType = resolveZoneType(requiredZoneTypeId);

        existing.setName(normalizedName);
        existing.setCode(normalizedCode);
        existing.setDisplayName(normalizedDisplay);
        existing.setDescription(normalizedDescription);
        existing.setRequiredZoneType(requiredZoneType);

        ProductCategory saved = productCategoryRepository.save(existing);
        CategoryResult after = toResult(saved);
        tenantAuditService.record("CATEGORY_UPDATE", "CATEGORY", after.id().toString(), before, after);
        return after;
    }

    /** Backward-compatible two-arg update used by legacy callers. */
    @Transactional
    public CategoryResult updateCategory(UUID categoryId, String name, String description) {
        ProductCategory existing = loadCategory(categoryId);
        return updateCategory(categoryId, name, existing.getCode(), name, description, 
                existing.getRequiredZoneType() != null ? existing.getRequiredZoneType().getId() : null);
    }

    @Transactional
    public void softDeleteCategory(UUID categoryId) {
        ProductCategory category = loadCategory(categoryId);
        CategoryResult before = toResult(category);

        if (!Boolean.FALSE.equals(category.getActive())) {
            category.setActive(false);
            category.setDeactivatedAt(Instant.now());
            productCategoryRepository.save(category);
        }

        tenantAuditService.record("CATEGORY_SOFT_DELETE", "CATEGORY", categoryId.toString(), before, toResult(category));
    }

    @Transactional
    public void restoreCategory(UUID categoryId) {
        ProductCategory category = loadCategory(categoryId);
        CategoryResult before = toResult(category);

        if (!Boolean.TRUE.equals(category.getActive()) || category.getDeactivatedAt() != null) {
            category.setActive(true);
            category.setDeactivatedAt(null);
            productCategoryRepository.save(category);
        }

        tenantAuditService.record("CATEGORY_RESTORE", "CATEGORY", categoryId.toString(), before, toResult(category));
    }

    @Transactional
    public void hardDeleteCategory(UUID categoryId) {
        ProductCategory category = loadCategory(categoryId);

        if (!Boolean.FALSE.equals(category.getActive())) {
            throw TenantCategoryManagementException.forbidden("Category must be inactive before hard delete");
        }

        long productReferences = productRepository.countByCategory_Id(categoryId);
        if (productReferences > 0) {
            throw TenantCategoryManagementException.conflict("Category cannot be hard deleted while referenced by products");
        }

        CategoryResult before = toResult(category);
        productCategoryRepository.delete(category);
        tenantAuditService.record("CATEGORY_HARD_DELETE", "CATEGORY", categoryId.toString(), before, null);
    }

    // ── package-visible helpers ───────────────────────────────────────────────

    ProductCategory loadCategory(UUID categoryId) {
        return productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> TenantCategoryManagementException.notFound("Category not found: " + categoryId));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private ZoneType resolveZoneType(UUID zoneTypeId) {
        if (zoneTypeId == null) return null;
        ZoneType zt = zoneTypeRepository.findById(zoneTypeId)
                .orElseThrow(() -> TenantCategoryManagementException.badRequest("Zone type not found: " + zoneTypeId));
        if (!Boolean.TRUE.equals(zt.getIsActive())) {
            throw TenantCategoryManagementException.badRequest("Zone type is inactive: " + zoneTypeId);
        }
        return zt;
    }

    private void assertCodeUnique(String code, UUID excludeId) {
        if (excludeId == null) {
            productCategoryRepository.findByCodeIgnoreCase(code)
                    .ifPresent(c -> { throw TenantCategoryManagementException.conflict("Category code already exists: " + code); });
        } else {
            if (productCategoryRepository.existsByCodeIgnoreCaseAndIdNot(code, excludeId)) {
                throw TenantCategoryManagementException.conflict("Category code already exists: " + code);
            }
        }
    }

    private Specification<ProductCategory> buildSpecification(String search, Boolean active) {
        String normalizedSearch = normalizeSearch(search);
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            if (normalizedSearch != null) {
                String value = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), value),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), value)));
            }

            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private String normalizeSearch(String search) {
        if (search == null) return null;
        String normalized = search.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw TenantCategoryManagementException.badRequest("name must not be blank");
        }
        String normalized = name.trim();
        if (normalized.length() > 120) {
            throw TenantCategoryManagementException.badRequest("name must be at most 120 characters");
        }
        return normalized;
    }

    static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw TenantCategoryManagementException.badRequest("code must not be blank");
        }
        return code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
    }

    private static String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw TenantCategoryManagementException.badRequest("displayName must not be blank");
        }
        String trimmed = displayName.trim();
        if (trimmed.length() > 120) {
            throw TenantCategoryManagementException.badRequest("displayName must be at most 120 characters");
        }
        return trimmed;
    }

    private String normalizeOptional(String value, int maxLength, String field) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maxLength) {
            throw TenantCategoryManagementException.badRequest(field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private CategoryResult toResult(ProductCategory category) {
        ZoneType zt = category.getRequiredZoneType();
        return new CategoryResult(
                category.getId(),
                category.getName(),
                category.getCode(),
                category.getDisplayName(),
                category.getDescription(),
                zt != null ? zt.getId() : null,
                zt != null ? zt.getCode() : null,
                !Boolean.FALSE.equals(category.getActive()),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                category.getDeactivatedAt());
    }

    public record CategoryResult(
            UUID id,
            String name,
            String code,
            String displayName,
            String description,
            UUID requiredZoneTypeId,
            String requiredZoneTypeCode,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Instant deactivatedAt) {
    }

    public record CategoryPageResult(
            List<CategoryResult> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
