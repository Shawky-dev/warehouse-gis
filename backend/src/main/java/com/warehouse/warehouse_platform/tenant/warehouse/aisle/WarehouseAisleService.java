package com.warehouse.warehouse_platform.tenant.warehouse.aisle;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayout;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayoutRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.shelf.WarehouseShelf;
import com.warehouse.warehouse_platform.tenant.warehouse.shelf.WarehouseShelfRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.side.WarehouseAisleSideRepository;
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
public class WarehouseAisleService {

    private final WarehouseLayoutRepository layoutRepository;
    private final WarehouseAisleRepository aisleRepository;
    private final WarehouseAisleSideRepository sideRepository;
    private final WarehouseShelfRepository shelfRepository;
    private final TenantAuditService tenantAuditService;

    public WarehouseAisleService(
            WarehouseLayoutRepository layoutRepository,
            WarehouseAisleRepository aisleRepository,
            WarehouseAisleSideRepository sideRepository,
            WarehouseShelfRepository shelfRepository,
            TenantAuditService tenantAuditService) {
        this.layoutRepository = layoutRepository;
        this.aisleRepository = aisleRepository;
        this.sideRepository = sideRepository;
        this.shelfRepository = shelfRepository;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public AislePageResult listAisles(UUID layoutId, int page, int size, String search, Boolean active) {
        loadLayout(layoutId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"));
        Specification<WarehouseAisle> spec = buildSpecification(layoutId, search, active);
        Page<WarehouseAisle> result = aisleRepository.findAll(spec, pageable);
        return new AislePageResult(
                result.getContent().stream().map(this::toResult).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AisleResult getAisle(UUID aisleId) {
        return toResult(loadAisle(aisleId));
    }

    @Transactional
    public AisleResult createAisle(UUID layoutId, String code, String name) {
        WarehouseLayout layout = loadLayout(layoutId);
        String normalizedCode = normalizeCode(code);
        String normalizedName = normalizeOptional(name, 120, "name");

        aisleRepository.findByLayout_IdAndCodeIgnoreCase(layoutId, normalizedCode)
                .ifPresent(existing -> {
                    throw WarehouseManagementException.conflict("Aisle code already exists in this layout: " + normalizedCode);
                });

        WarehouseAisle aisle = WarehouseAisle.builder()
                .layout(layout)
                .code(normalizedCode)
                .name(normalizedName)
                .active(true)
                .build();

        WarehouseAisle saved = aisleRepository.save(aisle);
        AisleResult result = toResult(saved);
        tenantAuditService.record("WAREHOUSE_AISLE_CREATE", "WAREHOUSE_AISLE", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public AisleResult updateAisle(UUID aisleId, String code, String name) {
        WarehouseAisle existing = loadAisle(aisleId);
        AisleResult before = toResult(existing);

        String normalizedCode = normalizeCode(code);
        String normalizedName = normalizeOptional(name, 120, "name");

        aisleRepository.findByLayout_IdAndCodeIgnoreCase(existing.getLayout().getId(), normalizedCode)
                .filter(found -> !found.getId().equals(aisleId))
                .ifPresent(found -> {
                    throw WarehouseManagementException.conflict("Aisle code already exists in this layout: " + normalizedCode);
                });

        String oldCode = existing.getCode();
        boolean codeChanged = !oldCode.equals(normalizedCode);

        existing.setCode(normalizedCode);
        existing.setName(normalizedName);

        WarehouseAisle saved = aisleRepository.save(existing);

        if (codeChanged) {
            cascadeRenameAisleCode(aisleId, oldCode, normalizedCode);
        }

        AisleResult after = toResult(saved);
        tenantAuditService.record("WAREHOUSE_AISLE_UPDATE", "WAREHOUSE_AISLE", after.id().toString(), before, after);
        return after;
    }

    @Transactional
    public void softDeleteAisle(UUID aisleId) {
        WarehouseAisle aisle = loadAisle(aisleId);
        AisleResult before = toResult(aisle);

        if (!Boolean.FALSE.equals(aisle.getActive())) {
            aisle.setActive(false);
            aisle.setDeactivatedAt(Instant.now());
            aisleRepository.save(aisle);
        }

        tenantAuditService.record("WAREHOUSE_AISLE_SOFT_DELETE", "WAREHOUSE_AISLE", aisleId.toString(), before, toResult(aisle));
    }

    @Transactional
    public void restoreAisle(UUID aisleId) {
        WarehouseAisle aisle = loadAisle(aisleId);
        AisleResult before = toResult(aisle);

        if (!Boolean.TRUE.equals(aisle.getActive()) || aisle.getDeactivatedAt() != null) {
            aisle.setActive(true);
            aisle.setDeactivatedAt(null);
            aisleRepository.save(aisle);
        }

        tenantAuditService.record("WAREHOUSE_AISLE_RESTORE", "WAREHOUSE_AISLE", aisleId.toString(), before, toResult(aisle));
    }

    @Transactional
    public void hardDeleteAisle(UUID aisleId) {
        WarehouseAisle aisle = loadAisle(aisleId);

        if (!Boolean.FALSE.equals(aisle.getActive())) {
            throw WarehouseManagementException.forbidden("Aisle must be inactive before hard delete");
        }

        long sideCount = sideRepository.countByAisle_Id(aisleId);
        if (sideCount > 0) {
            throw WarehouseManagementException.conflict("Aisle cannot be hard deleted while it has sides");
        }

        AisleResult before = toResult(aisle);
        aisleRepository.delete(aisle);
        tenantAuditService.record("WAREHOUSE_AISLE_HARD_DELETE", "WAREHOUSE_AISLE", aisleId.toString(), before, null);
    }

    private void cascadeRenameAisleCode(UUID aisleId, String oldCode, String newCode) {
        List<WarehouseShelf> shelves = shelfRepository.findAllByAisleId(aisleId);
        for (WarehouseShelf shelf : shelves) {
            String loc = shelf.getLocationCode();
            if (loc == null) {
                continue;
            }
            String[] parts = loc.split("-", -1);
            if (parts.length >= 6) {
                parts[1] = newCode;
                shelf.setLocationCode(String.join("-", parts));
            }
        }
        shelfRepository.saveAll(shelves);
    }

    private WarehouseLayout loadLayout(UUID layoutId) {
        return layoutRepository.findById(layoutId)
                .orElseThrow(() -> WarehouseManagementException.notFound("Layout not found: " + layoutId));
    }

    private WarehouseAisle loadAisle(UUID aisleId) {
        return aisleRepository.findById(aisleId)
                .orElseThrow(() -> WarehouseManagementException.notFound("Aisle not found: " + aisleId));
    }

    private Specification<WarehouseAisle> buildSpecification(UUID layoutId, String search, Boolean active) {
        String normalizedSearch = normalizeSearch(search);
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(root.get("layout").get("id"), layoutId));

            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            if (normalizedSearch != null) {
                String value = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), value),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), value)));
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
            throw WarehouseManagementException.badRequest("code must not be blank");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 20) {
            throw WarehouseManagementException.badRequest("code must be at most 20 characters");
        }
        if (!normalized.matches("[A-Z0-9]+")) {
            throw WarehouseManagementException.badRequest("code must contain only uppercase alphanumeric characters");
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
            throw WarehouseManagementException.badRequest(field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private AisleResult toResult(WarehouseAisle aisle) {
        return new AisleResult(
                aisle.getId(),
                aisle.getLayout().getId(),
                aisle.getLayout().getCode(),
                aisle.getCode(),
                aisle.getName(),
                !Boolean.FALSE.equals(aisle.getActive()),
                aisle.getCreatedAt(),
                aisle.getUpdatedAt(),
                aisle.getDeactivatedAt());
    }

    public record AisleResult(
            UUID id,
            UUID layoutId,
            String layoutCode,
            String code,
            String name,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Instant deactivatedAt) {
    }

    public record AislePageResult(
            List<AisleResult> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
