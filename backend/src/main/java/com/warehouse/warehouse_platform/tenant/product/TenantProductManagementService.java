package com.warehouse.warehouse_platform.tenant.product;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.category.ProductCategory;
import com.warehouse.warehouse_platform.tenant.category.ProductCategoryRepository;
import com.warehouse.warehouse_platform.tenant.supplier.Supplier;
import com.warehouse.warehouse_platform.tenant.supplier.SupplierRepository;
import com.warehouse.warehouse_platform.tenant.uom.UnitOfMeasure;
import com.warehouse.warehouse_platform.tenant.uom.UnitOfMeasureRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TenantProductManagementService {

    private final ProductRepository productRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final SupplierRepository supplierRepository;
    private final ProductSupplierRepository productSupplierRepository;
    private final TenantAuditService tenantAuditService;

    public TenantProductManagementService(
            ProductRepository productRepository,
            UnitOfMeasureRepository unitOfMeasureRepository,
            ProductCategoryRepository productCategoryRepository,
            SupplierRepository supplierRepository,
            ProductSupplierRepository productSupplierRepository,
            TenantAuditService tenantAuditService) {
        this.productRepository = productRepository;
        this.unitOfMeasureRepository = unitOfMeasureRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.supplierRepository = supplierRepository;
        this.productSupplierRepository = productSupplierRepository;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public ProductPageResult listProducts(
            int page,
            int size,
            String search,
            Boolean active,
            UUID baseUomId,
            UUID supplierId,
            UUID categoryId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sku"));
        Page<Product> products = productRepository.findAll(
                buildSpecification(search, active, baseUomId, supplierId, categoryId),
                pageable);

        Map<UUID, List<ProductSupplier>> mappingsByProductId;
        if (products.getContent().isEmpty()) {
            mappingsByProductId = Map.of();
        } else {
            mappingsByProductId = productSupplierRepository
                    .findAllByProduct_IdIn(products.getContent().stream().map(Product::getId).toList())
                    .stream()
                    .collect(Collectors.groupingBy(mapping -> mapping.getProduct().getId()));
        }

        List<ProductResult> content = products.getContent().stream()
                .map(product -> toResult(product, mappingsByProductId.getOrDefault(product.getId(), List.of())))
                .toList();

        return new ProductPageResult(
                content,
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ProductResult getProduct(UUID productId) {
        Product product = loadProduct(productId);
        List<ProductSupplier> mappings = productSupplierRepository.findAllByProduct_Id(productId);
        return toResult(product, mappings);
    }

    @Transactional
    public ProductResult createProduct(
            String sku,
            String name,
            String description,
            UUID baseUomId,
            UUID categoryId,
            Boolean trackLot,
            Boolean trackExpiry,
            Set<UUID> supplierIds,
            UUID primarySupplierId) {
        String normalizedSku = normalizeSku(sku);
        String normalizedName = normalizeName(name);
        String normalizedDescription = normalizeOptional(description, 1000, "description");
        boolean normalizedTrackLot = normalizeBoolean(trackLot, false);
        boolean normalizedTrackExpiry = normalizeBoolean(trackExpiry, false);

        productRepository.findBySkuIgnoreCase(normalizedSku)
                .ifPresent(existing -> {
                    throw TenantProductManagementException.conflict("Product SKU already exists: " + normalizedSku);
                });

        UnitOfMeasure baseUom = loadBaseUom(baseUomId);
        ProductCategory category = loadCategoryIfProvided(categoryId);
        SupplierResolution supplierResolution = resolveSuppliers(supplierIds, primarySupplierId);

        Product product = Product.builder()
                .sku(normalizedSku)
                .name(normalizedName)
                .description(normalizedDescription)
                .baseUom(baseUom)
                .category(category)
                .trackLot(normalizedTrackLot)
                .trackExpiry(normalizedTrackExpiry)
                .active(true)
                .build();

        Product saved = productRepository.save(product);
        List<ProductSupplier> mappings = replaceProductSuppliers(saved, supplierResolution);

        ProductResult result = toResult(saved, mappings);
        tenantAuditService.record("PRODUCT_CREATE", "PRODUCT", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public ProductResult updateProduct(
            UUID productId,
            String sku,
            String name,
            String description,
            UUID baseUomId,
            UUID categoryId,
            Boolean trackLot,
            Boolean trackExpiry,
            Set<UUID> supplierIds,
            UUID primarySupplierId) {
        Product existing = loadProduct(productId);
        ProductResult before = getProduct(productId);

        String normalizedSku = normalizeSku(sku);
        String normalizedName = normalizeName(name);

        productRepository.findBySkuIgnoreCase(normalizedSku)
                .filter(found -> !found.getId().equals(productId))
                .ifPresent(found -> {
                    throw TenantProductManagementException.conflict("Product SKU already exists: " + normalizedSku);
                });

        UnitOfMeasure baseUom = loadBaseUom(baseUomId);
        ProductCategory category = loadCategoryIfProvided(categoryId);
        SupplierResolution supplierResolution = resolveSuppliers(supplierIds, primarySupplierId);

        existing.setSku(normalizedSku);
        existing.setName(normalizedName);
        existing.setDescription(normalizeOptional(description, 1000, "description"));
        existing.setBaseUom(baseUom);
        existing.setCategory(category);
        existing.setTrackLot(normalizeBoolean(trackLot, false));
        existing.setTrackExpiry(normalizeBoolean(trackExpiry, false));

        Product saved = productRepository.save(existing);
        List<ProductSupplier> mappings = replaceProductSuppliers(saved, supplierResolution);

        ProductResult after = toResult(saved, mappings);
        tenantAuditService.record("PRODUCT_UPDATE", "PRODUCT", productId.toString(), before, after);
        return after;
    }

    @Transactional
    public void softDeleteProduct(UUID productId) {
        Product product = loadProduct(productId);
        ProductResult before = getProduct(productId);

        if (!Boolean.FALSE.equals(product.getActive())) {
            product.setActive(false);
            product.setDeactivatedAt(Instant.now());
            productRepository.save(product);
        }

        tenantAuditService.record("PRODUCT_SOFT_DELETE", "PRODUCT", productId.toString(), before, getProduct(productId));
    }

    @Transactional
    public void restoreProduct(UUID productId) {
        Product product = loadProduct(productId);
        ProductResult before = getProduct(productId);

        if (!Boolean.TRUE.equals(product.getActive()) || product.getDeactivatedAt() != null) {
            product.setActive(true);
            product.setDeactivatedAt(null);
            productRepository.save(product);
        }

        tenantAuditService.record("PRODUCT_RESTORE", "PRODUCT", productId.toString(), before, getProduct(productId));
    }

    @Transactional
    public void hardDeleteProduct(UUID productId) {
        Product product = loadProduct(productId);

        if (!Boolean.FALSE.equals(product.getActive())) {
            throw TenantProductManagementException.forbidden("Product must be inactive before hard delete");
        }

        long supplierReferences = productSupplierRepository.countByProduct_Id(productId);
        if (supplierReferences > 0) {
            throw TenantProductManagementException.conflict("Product cannot be hard deleted while linked to suppliers");
        }

        ProductResult before = toResult(product, List.of());
        productRepository.delete(product);
        tenantAuditService.record("PRODUCT_HARD_DELETE", "PRODUCT", productId.toString(), before, null);
    }

    private Product loadProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> TenantProductManagementException.notFound("Product not found: " + productId));
    }

    private UnitOfMeasure loadBaseUom(UUID baseUomId) {
        if (baseUomId == null) {
            throw TenantProductManagementException.badRequest("baseUomId must not be null");
        }
        return unitOfMeasureRepository.findById(baseUomId)
                .orElseThrow(() -> TenantProductManagementException.badRequest("Base UOM not found: " + baseUomId));
    }

    private ProductCategory loadCategoryIfProvided(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> TenantProductManagementException.badRequest("Category not found: " + categoryId));
    }

    private SupplierResolution resolveSuppliers(Set<UUID> supplierIds, UUID primarySupplierId) {
        Set<UUID> normalizedSupplierIds = normalizeSupplierIds(supplierIds);

        if (primarySupplierId != null && !normalizedSupplierIds.contains(primarySupplierId)) {
            throw TenantProductManagementException.badRequest("primarySupplierId must be included in supplierIds");
        }

        if (normalizedSupplierIds.isEmpty()) {
            if (primarySupplierId != null) {
                throw TenantProductManagementException.badRequest("primarySupplierId requires at least one supplier");
            }
            return new SupplierResolution(Map.of(), null);
        }

        Map<UUID, Supplier> suppliersById = supplierRepository.findAllById(normalizedSupplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));

        if (suppliersById.size() != normalizedSupplierIds.size()) {
            Set<UUID> missing = new HashSet<>(normalizedSupplierIds);
            missing.removeAll(suppliersById.keySet());
            throw TenantProductManagementException.badRequest("Unknown supplier ids: " + missing);
        }

        return new SupplierResolution(suppliersById, primarySupplierId);
    }

    private Set<UUID> normalizeSupplierIds(Set<UUID> supplierIds) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return Set.of();
        }

        Set<UUID> normalized = supplierIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        if (normalized.size() != supplierIds.size()) {
            throw TenantProductManagementException.badRequest("supplierIds must not contain null values");
        }

        return normalized;
    }

    private List<ProductSupplier> replaceProductSuppliers(Product product, SupplierResolution supplierResolution) {
        productSupplierRepository.deleteByProduct_Id(product.getId());

        if (supplierResolution.suppliersById().isEmpty()) {
            return List.of();
        }

        List<ProductSupplier> mappings = supplierResolution.suppliersById().values().stream()
                .sorted(Comparator.comparing(Supplier::getCode))
                .map(supplier -> ProductSupplier.builder()
                        .id(new ProductSupplierId(product.getId(), supplier.getId()))
                        .product(product)
                        .supplier(supplier)
                        .primary(supplier.getId().equals(supplierResolution.primarySupplierId()))
                        .build())
                .toList();

        return productSupplierRepository.saveAll(mappings);
    }

    private Specification<Product> buildSpecification(
            String search,
            Boolean active,
            UUID baseUomId,
            UUID supplierId,
            UUID categoryId) {
        String normalizedSearch = normalizeSearch(search);
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }
            if (baseUomId != null) {
                predicates.add(criteriaBuilder.equal(root.get("baseUom").get("id"), baseUomId));
            }
            if (categoryId != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), categoryId));
            }
            if (normalizedSearch != null) {
                String value = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("sku")), value),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), value)));
            }
            if (supplierId != null) {
                jakarta.persistence.criteria.Subquery<UUID> supplierSubquery = query.subquery(UUID.class);
                jakarta.persistence.criteria.Root<ProductSupplier> supplierRoot = supplierSubquery.from(ProductSupplier.class);
                supplierSubquery.select(supplierRoot.get("product").get("id"));
                supplierSubquery.where(
                        criteriaBuilder.equal(supplierRoot.get("product").get("id"), root.get("id")),
                        criteriaBuilder.equal(supplierRoot.get("supplier").get("id"), supplierId));
                predicates.add(criteriaBuilder.exists(supplierSubquery));
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

    private String normalizeSku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw TenantProductManagementException.badRequest("sku must not be blank");
        }
        String normalized = sku.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 60) {
            throw TenantProductManagementException.badRequest("sku must be at most 60 characters");
        }
        return normalized;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw TenantProductManagementException.badRequest("name must not be blank");
        }
        String normalized = name.trim();
        if (normalized.length() > 200) {
            throw TenantProductManagementException.badRequest("name must be at most 200 characters");
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
            throw TenantProductManagementException.badRequest(field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private boolean normalizeBoolean(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private ProductResult toResult(Product product, List<ProductSupplier> mappings) {
        List<ProductSupplierResult> suppliers = mappings.stream()
                .sorted(Comparator.comparing(mapping -> mapping.getSupplier().getCode()))
                .map(mapping -> new ProductSupplierResult(
                        mapping.getSupplier().getId(),
                        mapping.getSupplier().getCode(),
                        mapping.getSupplier().getName(),
                        Boolean.TRUE.equals(mapping.getPrimary())))
                .toList();

        ProductCategory category = product.getCategory();

        return new ProductResult(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getBaseUom().getId(),
                product.getBaseUom().getCode(),
                product.getBaseUom().getName(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                !Boolean.FALSE.equals(product.getTrackLot()),
                !Boolean.FALSE.equals(product.getTrackExpiry()),
                !Boolean.FALSE.equals(product.getActive()),
                suppliers,
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getDeactivatedAt());
    }

    private record SupplierResolution(Map<UUID, Supplier> suppliersById, UUID primarySupplierId) {
    }

    public record ProductSupplierResult(
            UUID supplierId,
            String supplierCode,
            String supplierName,
            boolean primary) {
    }

    public record ProductResult(
            UUID id,
            String sku,
            String name,
            String description,
            UUID baseUomId,
            String baseUomCode,
            String baseUomName,
            UUID categoryId,
            String categoryName,
            boolean trackLot,
            boolean trackExpiry,
            boolean active,
            List<ProductSupplierResult> suppliers,
            Instant createdAt,
            Instant updatedAt,
            Instant deactivatedAt) {
    }

    public record ProductPageResult(
            List<ProductResult> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
