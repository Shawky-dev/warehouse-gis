package com.warehouse.warehouse_platform.tenant.uom;

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
public class TenantUomManagementService {

    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final ProductRepository productRepository;
    private final TenantAuditService tenantAuditService;

    public TenantUomManagementService(
            UnitOfMeasureRepository unitOfMeasureRepository,
            ProductRepository productRepository,
            TenantAuditService tenantAuditService) {
        this.unitOfMeasureRepository = unitOfMeasureRepository;
        this.productRepository = productRepository;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public UomPageResult listUoms(int page, int size, String search, Boolean active) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"));
        Page<UnitOfMeasure> result = unitOfMeasureRepository.findAll(buildSpecification(search, active), pageable);
        return new UomPageResult(
                result.getContent().stream().map(this::toResult).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UomResult getUom(UUID uomId) {
        return toResult(loadUom(uomId));
    }

    @Transactional
    public UomResult createUom(String code, String name, String symbol) {
        String normalizedCode = normalizeCode(code);
        String normalizedName = normalizeName(name);
        String normalizedSymbol = normalizeOptional(symbol, 20, "symbol");

        unitOfMeasureRepository.findByCodeIgnoreCase(normalizedCode)
                .ifPresent(existing -> {
                    throw TenantUomManagementException.conflict("UOM code already exists: " + normalizedCode);
                });

        UnitOfMeasure uom = UnitOfMeasure.builder()
                .code(normalizedCode)
                .name(normalizedName)
                .symbol(normalizedSymbol)
                .active(true)
                .build();

        UnitOfMeasure saved = unitOfMeasureRepository.save(uom);
        UomResult result = toResult(saved);
        tenantAuditService.record("UOM_CREATE", "UOM", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public UomResult updateUom(UUID uomId, String code, String name, String symbol) {
        UnitOfMeasure existing = loadUom(uomId);
        UomResult before = toResult(existing);

        String normalizedCode = normalizeCode(code);
        String normalizedName = normalizeName(name);
        String normalizedSymbol = normalizeOptional(symbol, 20, "symbol");

        unitOfMeasureRepository.findByCodeIgnoreCase(normalizedCode)
                .filter(found -> !found.getId().equals(uomId))
                .ifPresent(found -> {
                    throw TenantUomManagementException.conflict("UOM code already exists: " + normalizedCode);
                });

        existing.setCode(normalizedCode);
        existing.setName(normalizedName);
        existing.setSymbol(normalizedSymbol);

        UnitOfMeasure saved = unitOfMeasureRepository.save(existing);
        UomResult after = toResult(saved);
        tenantAuditService.record("UOM_UPDATE", "UOM", after.id().toString(), before, after);
        return after;
    }

    @Transactional
    public void softDeleteUom(UUID uomId) {
        UnitOfMeasure uom = loadUom(uomId);
        UomResult before = toResult(uom);

        if (!Boolean.FALSE.equals(uom.getActive())) {
            uom.setActive(false);
            uom.setDeactivatedAt(Instant.now());
            unitOfMeasureRepository.save(uom);
        }

        tenantAuditService.record("UOM_SOFT_DELETE", "UOM", uomId.toString(), before, toResult(uom));
    }

    @Transactional
    public void restoreUom(UUID uomId) {
        UnitOfMeasure uom = loadUom(uomId);
        UomResult before = toResult(uom);

        if (!Boolean.TRUE.equals(uom.getActive()) || uom.getDeactivatedAt() != null) {
            uom.setActive(true);
            uom.setDeactivatedAt(null);
            unitOfMeasureRepository.save(uom);
        }

        tenantAuditService.record("UOM_RESTORE", "UOM", uomId.toString(), before, toResult(uom));
    }

    @Transactional
    public void hardDeleteUom(UUID uomId) {
        UnitOfMeasure uom = loadUom(uomId);

        if (!Boolean.FALSE.equals(uom.getActive())) {
            throw TenantUomManagementException.forbidden("UOM must be inactive before hard delete");
        }

        long productReferences = productRepository.countByBaseUom_Id(uomId);
        if (productReferences > 0) {
            throw TenantUomManagementException.conflict("UOM cannot be hard deleted while referenced by products");
        }

        UomResult before = toResult(uom);
        unitOfMeasureRepository.delete(uom);
        tenantAuditService.record("UOM_HARD_DELETE", "UOM", uomId.toString(), before, null);
    }

    private UnitOfMeasure loadUom(UUID uomId) {
        return unitOfMeasureRepository.findById(uomId)
                .orElseThrow(() -> TenantUomManagementException.notFound("UOM not found: " + uomId));
    }

    private Specification<UnitOfMeasure> buildSpecification(String search, Boolean active) {
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
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("symbol")), value)));
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
            throw TenantUomManagementException.badRequest("code must not be blank");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 40) {
            throw TenantUomManagementException.badRequest("code must be at most 40 characters");
        }
        return normalized;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw TenantUomManagementException.badRequest("name must not be blank");
        }
        String normalized = name.trim();
        if (normalized.length() > 120) {
            throw TenantUomManagementException.badRequest("name must be at most 120 characters");
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
            throw TenantUomManagementException.badRequest(field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private UomResult toResult(UnitOfMeasure uom) {
        return new UomResult(
                uom.getId(),
                uom.getCode(),
                uom.getName(),
                uom.getSymbol(),
                !Boolean.FALSE.equals(uom.getActive()),
                uom.getCreatedAt(),
                uom.getUpdatedAt(),
                uom.getDeactivatedAt());
    }

    public record UomResult(
            UUID id,
            String code,
            String name,
            String symbol,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Instant deactivatedAt) {
    }

    public record UomPageResult(
            List<UomResult> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
