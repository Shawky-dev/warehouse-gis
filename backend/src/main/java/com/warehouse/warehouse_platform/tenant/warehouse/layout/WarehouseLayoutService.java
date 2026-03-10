package com.warehouse.warehouse_platform.tenant.warehouse.layout;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.aisle.WarehouseAisleRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import com.warehouse.warehouse_platform.tenant.warehouse.shelf.WarehouseShelf;
import com.warehouse.warehouse_platform.tenant.warehouse.shelf.WarehouseShelfRepository;
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
public class WarehouseLayoutService {

    private final WarehouseLayoutRepository layoutRepository;
    private final WarehouseAisleRepository aisleRepository;
    private final WarehouseShelfRepository shelfRepository;
    private final TenantAuditService tenantAuditService;

    public WarehouseLayoutService(
            WarehouseLayoutRepository layoutRepository,
            WarehouseAisleRepository aisleRepository,
            WarehouseShelfRepository shelfRepository,
            TenantAuditService tenantAuditService) {
        this.layoutRepository = layoutRepository;
        this.aisleRepository = aisleRepository;
        this.shelfRepository = shelfRepository;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public LayoutPageResult listLayouts(int page, int size, String search, Boolean active) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"));
        Page<WarehouseLayout> result = layoutRepository.findAll(buildSpecification(search, active), pageable);
        return new LayoutPageResult(
                result.getContent().stream().map(this::toResult).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public LayoutResult getLayout(UUID id) {
        return toResult(loadLayout(id));
    }

    @Transactional
    public LayoutResult createLayout(String code, String name, String desc) {
        String normalizedCode = normalizeCode(code);
        String normalizedName = normalizeName(name);
        String normalizedDesc = normalizeOptional(desc, 500, "description");

        layoutRepository.findByCodeIgnoreCase(normalizedCode)
                .ifPresent(existing -> {
                    throw WarehouseManagementException.conflict("Layout code already exists: " + normalizedCode);
                });

        WarehouseLayout layout = WarehouseLayout.builder()
                .code(normalizedCode)
                .name(normalizedName)
                .description(normalizedDesc)
                .active(true)
                .build();

        WarehouseLayout saved = layoutRepository.save(layout);
        LayoutResult result = toResult(saved);
        tenantAuditService.record("WAREHOUSE_LAYOUT_CREATE", "WAREHOUSE_LAYOUT", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public LayoutResult updateLayout(UUID id, String code, String name, String desc) {
        WarehouseLayout existing = loadLayout(id);
        LayoutResult before = toResult(existing);

        String normalizedCode = normalizeCode(code);
        String normalizedName = normalizeName(name);
        String normalizedDesc = normalizeOptional(desc, 500, "description");

        layoutRepository.findByCodeIgnoreCase(normalizedCode)
                .filter(found -> !found.getId().equals(id))
                .ifPresent(found -> {
                    throw WarehouseManagementException.conflict("Layout code already exists: " + normalizedCode);
                });

        String oldCode = existing.getCode();
        boolean codeChanged = !oldCode.equals(normalizedCode);

        existing.setCode(normalizedCode);
        existing.setName(normalizedName);
        existing.setDescription(normalizedDesc);

        WarehouseLayout saved = layoutRepository.save(existing);

        if (codeChanged) {
            cascadeRenameLayoutCode(id, oldCode, normalizedCode);
        }

        LayoutResult after = toResult(saved);
        tenantAuditService.record("WAREHOUSE_LAYOUT_UPDATE", "WAREHOUSE_LAYOUT", after.id().toString(), before, after);
        return after;
    }

    @Transactional
    public void softDeleteLayout(UUID id) {
        WarehouseLayout layout = loadLayout(id);
        LayoutResult before = toResult(layout);

        if (!Boolean.FALSE.equals(layout.getActive())) {
            layout.setActive(false);
            layout.setDeactivatedAt(Instant.now());
            layoutRepository.save(layout);
        }

        tenantAuditService.record("WAREHOUSE_LAYOUT_SOFT_DELETE", "WAREHOUSE_LAYOUT", id.toString(), before, toResult(layout));
    }

    @Transactional
    public void restoreLayout(UUID id) {
        WarehouseLayout layout = loadLayout(id);
        LayoutResult before = toResult(layout);

        if (!Boolean.TRUE.equals(layout.getActive()) || layout.getDeactivatedAt() != null) {
            layout.setActive(true);
            layout.setDeactivatedAt(null);
            layoutRepository.save(layout);
        }

        tenantAuditService.record("WAREHOUSE_LAYOUT_RESTORE", "WAREHOUSE_LAYOUT", id.toString(), before, toResult(layout));
    }

    @Transactional
    public void hardDeleteLayout(UUID id) {
        WarehouseLayout layout = loadLayout(id);

        if (!Boolean.FALSE.equals(layout.getActive())) {
            throw WarehouseManagementException.forbidden("Layout must be inactive before hard delete");
        }

        long aisleCount = aisleRepository.countByLayout_Id(id);
        if (aisleCount > 0) {
            throw WarehouseManagementException.conflict("Layout cannot be hard deleted while it has aisles");
        }

        LayoutResult before = toResult(layout);
        layoutRepository.delete(layout);
        tenantAuditService.record("WAREHOUSE_LAYOUT_HARD_DELETE", "WAREHOUSE_LAYOUT", id.toString(), before, null);
    }

    private void cascadeRenameLayoutCode(UUID layoutId, String oldCode, String newCode) {
        List<WarehouseShelf> shelves = shelfRepository.findAllByLayoutId(layoutId);
        String oldPrefix = oldCode + "-";
        String newPrefix = newCode + "-";
        for (WarehouseShelf shelf : shelves) {
            String loc = shelf.getLocationCode();
            if (loc != null && loc.startsWith(oldPrefix)) {
                shelf.setLocationCode(newPrefix + loc.substring(oldPrefix.length()));
            }
        }
        shelfRepository.saveAll(shelves);
    }

    private WarehouseLayout loadLayout(UUID id) {
        return layoutRepository.findById(id)
                .orElseThrow(() -> WarehouseManagementException.notFound("Layout not found: " + id));
    }

    private Specification<WarehouseLayout> buildSpecification(String search, Boolean active) {
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

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw WarehouseManagementException.badRequest("name must not be blank");
        }
        String normalized = name.trim();
        if (normalized.length() > 120) {
            throw WarehouseManagementException.badRequest("name must be at most 120 characters");
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

    private LayoutResult toResult(WarehouseLayout layout) {
        return new LayoutResult(
                layout.getId(),
                layout.getCode(),
                layout.getName(),
                layout.getDescription(),
                !Boolean.FALSE.equals(layout.getActive()),
                layout.getCreatedAt(),
                layout.getUpdatedAt(),
                layout.getDeactivatedAt());
    }

    public record LayoutResult(
            UUID id,
            String code,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Instant deactivatedAt) {
    }

    public record LayoutPageResult(
            List<LayoutResult> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
