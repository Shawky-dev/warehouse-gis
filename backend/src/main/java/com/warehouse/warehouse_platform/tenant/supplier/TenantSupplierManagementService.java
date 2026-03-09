package com.warehouse.warehouse_platform.tenant.supplier;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.product.ProductSupplierRepository;
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
public class TenantSupplierManagementService {

    private final SupplierRepository supplierRepository;
    private final ProductSupplierRepository productSupplierRepository;
    private final TenantAuditService tenantAuditService;

    public TenantSupplierManagementService(
            SupplierRepository supplierRepository,
            ProductSupplierRepository productSupplierRepository,
            TenantAuditService tenantAuditService) {
        this.supplierRepository = supplierRepository;
        this.productSupplierRepository = productSupplierRepository;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public SupplierPageResult listSuppliers(int page, int size, String search, Boolean active) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"));
        Page<Supplier> result = supplierRepository.findAll(buildSpecification(search, active), pageable);
        return new SupplierPageResult(
                result.getContent().stream().map(this::toResult).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SupplierResult getSupplier(UUID supplierId) {
        return toResult(loadSupplier(supplierId));
    }

    @Transactional
    public SupplierResult createSupplier(
            String code,
            String name,
            String contactName,
            String contactEmail,
            String contactPhone,
            String notes) {
        String normalizedCode = normalizeCode(code);
        String normalizedName = normalizeName(name);

        supplierRepository.findByCodeIgnoreCase(normalizedCode)
                .ifPresent(existing -> {
                    throw TenantSupplierManagementException.conflict("Supplier code already exists: " + normalizedCode);
                });

        Supplier supplier = Supplier.builder()
                .code(normalizedCode)
                .name(normalizedName)
                .contactName(normalizeOptional(contactName, 160, "contactName"))
                .contactEmail(normalizeOptional(contactEmail, 255, "contactEmail"))
                .contactPhone(normalizeOptional(contactPhone, 60, "contactPhone"))
                .notes(normalizeOptional(notes, 500, "notes"))
                .active(true)
                .build();

        Supplier saved = supplierRepository.save(supplier);
        SupplierResult result = toResult(saved);
        tenantAuditService.record("SUPPLIER_CREATE", "SUPPLIER", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public SupplierResult updateSupplier(
            UUID supplierId,
            String code,
            String name,
            String contactName,
            String contactEmail,
            String contactPhone,
            String notes) {
        Supplier existing = loadSupplier(supplierId);
        SupplierResult before = toResult(existing);

        String normalizedCode = normalizeCode(code);
        String normalizedName = normalizeName(name);

        supplierRepository.findByCodeIgnoreCase(normalizedCode)
                .filter(found -> !found.getId().equals(supplierId))
                .ifPresent(found -> {
                    throw TenantSupplierManagementException.conflict("Supplier code already exists: " + normalizedCode);
                });

        existing.setCode(normalizedCode);
        existing.setName(normalizedName);
        existing.setContactName(normalizeOptional(contactName, 160, "contactName"));
        existing.setContactEmail(normalizeOptional(contactEmail, 255, "contactEmail"));
        existing.setContactPhone(normalizeOptional(contactPhone, 60, "contactPhone"));
        existing.setNotes(normalizeOptional(notes, 500, "notes"));

        Supplier saved = supplierRepository.save(existing);
        SupplierResult after = toResult(saved);
        tenantAuditService.record("SUPPLIER_UPDATE", "SUPPLIER", after.id().toString(), before, after);
        return after;
    }

    @Transactional
    public void softDeleteSupplier(UUID supplierId) {
        Supplier supplier = loadSupplier(supplierId);
        SupplierResult before = toResult(supplier);

        if (!Boolean.FALSE.equals(supplier.getActive())) {
            supplier.setActive(false);
            supplier.setDeactivatedAt(Instant.now());
            supplierRepository.save(supplier);
        }

        tenantAuditService.record("SUPPLIER_SOFT_DELETE", "SUPPLIER", supplierId.toString(), before, toResult(supplier));
    }

    @Transactional
    public void restoreSupplier(UUID supplierId) {
        Supplier supplier = loadSupplier(supplierId);
        SupplierResult before = toResult(supplier);

        if (!Boolean.TRUE.equals(supplier.getActive()) || supplier.getDeactivatedAt() != null) {
            supplier.setActive(true);
            supplier.setDeactivatedAt(null);
            supplierRepository.save(supplier);
        }

        tenantAuditService.record("SUPPLIER_RESTORE", "SUPPLIER", supplierId.toString(), before, toResult(supplier));
    }

    @Transactional
    public void hardDeleteSupplier(UUID supplierId) {
        Supplier supplier = loadSupplier(supplierId);

        if (!Boolean.FALSE.equals(supplier.getActive())) {
            throw TenantSupplierManagementException.forbidden("Supplier must be inactive before hard delete");
        }

        long references = productSupplierRepository.countBySupplier_Id(supplierId);
        if (references > 0) {
            throw TenantSupplierManagementException.conflict("Supplier cannot be hard deleted while linked to products");
        }

        SupplierResult before = toResult(supplier);
        supplierRepository.delete(supplier);
        tenantAuditService.record("SUPPLIER_HARD_DELETE", "SUPPLIER", supplierId.toString(), before, null);
    }

    private Supplier loadSupplier(UUID supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> TenantSupplierManagementException.notFound("Supplier not found: " + supplierId));
    }

    private Specification<Supplier> buildSpecification(String search, Boolean active) {
        String normalizedSearch = normalizeSearch(search);
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            if (normalizedSearch != null) {
                String value = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), value),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), value),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("contactName")), value),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("contactEmail")), value)));
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

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw TenantSupplierManagementException.badRequest("code must not be blank");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 40) {
            throw TenantSupplierManagementException.badRequest("code must be at most 40 characters");
        }
        return normalized;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw TenantSupplierManagementException.badRequest("name must not be blank");
        }
        String normalized = name.trim();
        if (normalized.length() > 160) {
            throw TenantSupplierManagementException.badRequest("name must be at most 160 characters");
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
            throw TenantSupplierManagementException.badRequest(field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private SupplierResult toResult(Supplier supplier) {
        return new SupplierResult(
                supplier.getId(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getContactName(),
                supplier.getContactEmail(),
                supplier.getContactPhone(),
                supplier.getNotes(),
                !Boolean.FALSE.equals(supplier.getActive()),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt(),
                supplier.getDeactivatedAt());
    }

    public record SupplierResult(
            UUID id,
            String code,
            String name,
            String contactName,
            String contactEmail,
            String contactPhone,
            String notes,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Instant deactivatedAt) {
    }

    public record SupplierPageResult(
            List<SupplierResult> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
