package com.warehouse.warehouse_platform.tenant.category;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
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
    private final TenantAuditService tenantAuditService;

    public TenantCategoryManagementService(
            ProductCategoryRepository productCategoryRepository,
            ProductRepository productRepository,
            TenantAuditService tenantAuditService) {
        this.productCategoryRepository = productCategoryRepository;
        this.productRepository = productRepository;
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
    public CategoryResult createCategory(String name, String description) {
        String normalizedName = normalizeName(name);
        String normalizedDescription = normalizeOptional(description, 500, "description");

        productCategoryRepository.findByNameIgnoreCase(normalizedName)
                .ifPresent(existing -> {
                    throw TenantCategoryManagementException.conflict("Category name already exists: " + normalizedName);
                });

        ProductCategory category = ProductCategory.builder()
                .name(normalizedName)
                .description(normalizedDescription)
                .active(true)
                .build();

        ProductCategory saved = productCategoryRepository.save(category);
        CategoryResult result = toResult(saved);
        tenantAuditService.record("CATEGORY_CREATE", "CATEGORY", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public CategoryResult updateCategory(UUID categoryId, String name, String description) {
        ProductCategory existing = loadCategory(categoryId);
        CategoryResult before = toResult(existing);

        String normalizedName = normalizeName(name);
        String normalizedDescription = normalizeOptional(description, 500, "description");

        productCategoryRepository.findByNameIgnoreCase(normalizedName)
                .filter(found -> !found.getId().equals(categoryId))
                .ifPresent(found -> {
                    throw TenantCategoryManagementException.conflict("Category name already exists: " + normalizedName);
                });

        existing.setName(normalizedName);
        existing.setDescription(normalizedDescription);

        ProductCategory saved = productCategoryRepository.save(existing);
        CategoryResult after = toResult(saved);
        tenantAuditService.record("CATEGORY_UPDATE", "CATEGORY", after.id().toString(), before, after);
        return after;
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

    private ProductCategory loadCategory(UUID categoryId) {
        return productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> TenantCategoryManagementException.notFound("Category not found: " + categoryId));
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
        if (search == null) {
            return null;
        }
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

    private String normalizeOptional(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw TenantCategoryManagementException.badRequest(field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private CategoryResult toResult(ProductCategory category) {
        return new CategoryResult(
                category.getId(),
                category.getName(),
                category.getDescription(),
                !Boolean.FALSE.equals(category.getActive()),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                category.getDeactivatedAt());
    }

    public record CategoryResult(
            UUID id,
            String name,
            String description,
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
