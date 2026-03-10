package com.warehouse.warehouse_platform.tenant.warehouse.bay;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.code.WarehouseLocationCodeGenerator;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import com.warehouse.warehouse_platform.tenant.warehouse.level.WarehouseBayLevel;
import com.warehouse.warehouse_platform.tenant.warehouse.level.WarehouseBayLevelRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.shelf.WarehouseShelf;
import com.warehouse.warehouse_platform.tenant.warehouse.shelf.WarehouseShelfRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.side.WarehouseAisleSide;
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
public class WarehouseBayService {

    private final WarehouseAisleSideRepository sideRepository;
    private final WarehouseBayRepository bayRepository;
    private final WarehouseBayLevelRepository levelRepository;
    private final WarehouseShelfRepository shelfRepository;
    private final WarehouseLocationCodeGenerator locationCodeGenerator;
    private final TenantAuditService tenantAuditService;

    public WarehouseBayService(
            WarehouseAisleSideRepository sideRepository,
            WarehouseBayRepository bayRepository,
            WarehouseBayLevelRepository levelRepository,
            WarehouseShelfRepository shelfRepository,
            WarehouseLocationCodeGenerator locationCodeGenerator,
            TenantAuditService tenantAuditService) {
        this.sideRepository = sideRepository;
        this.bayRepository = bayRepository;
        this.levelRepository = levelRepository;
        this.shelfRepository = shelfRepository;
        this.locationCodeGenerator = locationCodeGenerator;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public BayPageResult listBays(UUID sideId, int page, int size, String search, Boolean active) {
        loadSide(sideId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"));
        Page<WarehouseBay> result = bayRepository.findAll(buildSpecification(sideId, search, active), pageable);
        return new BayPageResult(
                result.getContent().stream().map(this::toResult).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BayResult getBay(UUID bayId) {
        return toResult(loadBay(bayId));
    }

    @Transactional
    public BayResult createBay(UUID sideId, String code) {
        WarehouseAisleSide side = loadSide(sideId);
        String normalizedCode = normalizeCode(code);

        bayRepository.findBySide_IdAndCodeIgnoreCase(sideId, normalizedCode)
                .ifPresent(existing -> {
                    throw WarehouseManagementException.conflict("Bay code already exists for this side: " + normalizedCode);
                });

        WarehouseBay bay = WarehouseBay.builder()
                .side(side)
                .code(normalizedCode)
                .active(true)
                .build();

        WarehouseBay saved = bayRepository.save(bay);
        BayResult result = toResult(saved);
        tenantAuditService.record("WAREHOUSE_BAY_CREATE", "WAREHOUSE_BAY", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public BayResult updateBay(UUID bayId, String code) {
        WarehouseBay existing = loadBay(bayId);
        BayResult before = toResult(existing);

        String normalizedCode = normalizeCode(code);

        bayRepository.findBySide_IdAndCodeIgnoreCase(existing.getSide().getId(), normalizedCode)
                .filter(found -> !found.getId().equals(bayId))
                .ifPresent(found -> {
                    throw WarehouseManagementException.conflict("Bay code already exists for this side: " + normalizedCode);
                });

        String oldCode = existing.getCode();
        boolean codeChanged = !oldCode.equals(normalizedCode);

        existing.setCode(normalizedCode);
        WarehouseBay saved = bayRepository.save(existing);

        if (codeChanged) {
            cascadeRenameBayCode(bayId, oldCode, normalizedCode);
        }

        BayResult after = toResult(saved);
        tenantAuditService.record("WAREHOUSE_BAY_UPDATE", "WAREHOUSE_BAY", after.id().toString(), before, after);
        return after;
    }

    @Transactional
    public void softDeleteBay(UUID bayId) {
        WarehouseBay bay = loadBay(bayId);
        BayResult before = toResult(bay);

        if (!Boolean.FALSE.equals(bay.getActive())) {
            bay.setActive(false);
            bay.setDeactivatedAt(Instant.now());
            bayRepository.save(bay);
        }

        tenantAuditService.record("WAREHOUSE_BAY_SOFT_DELETE", "WAREHOUSE_BAY", bayId.toString(), before, toResult(bay));
    }

    @Transactional
    public void restoreBay(UUID bayId) {
        WarehouseBay bay = loadBay(bayId);
        BayResult before = toResult(bay);

        if (!Boolean.TRUE.equals(bay.getActive()) || bay.getDeactivatedAt() != null) {
            bay.setActive(true);
            bay.setDeactivatedAt(null);
            bayRepository.save(bay);
        }

        tenantAuditService.record("WAREHOUSE_BAY_RESTORE", "WAREHOUSE_BAY", bayId.toString(), before, toResult(bay));
    }

    @Transactional
    public void hardDeleteBay(UUID bayId) {
        WarehouseBay bay = loadBay(bayId);

        if (!Boolean.FALSE.equals(bay.getActive())) {
            throw WarehouseManagementException.forbidden("Bay must be inactive before hard delete");
        }

        long levelCount = levelRepository.countByBay_Id(bayId);
        if (levelCount > 0) {
            throw WarehouseManagementException.conflict("Bay cannot be hard deleted while it has levels");
        }

        BayResult before = toResult(bay);
        bayRepository.delete(bay);
        tenantAuditService.record("WAREHOUSE_BAY_HARD_DELETE", "WAREHOUSE_BAY", bayId.toString(), before, null);
    }

    @Transactional
    public BulkCreateResult createBaysBulk(UUID sideId, List<String> codes, int levelsPerBay, int shelvesPerLevel) {
        WarehouseAisleSide side = loadSide(sideId);

        List<String> locationCodes = new ArrayList<>();

        for (String code : codes) {
            String normalizedCode = normalizeCode(code);

            bayRepository.findBySide_IdAndCodeIgnoreCase(sideId, normalizedCode)
                    .ifPresent(existing -> {
                        throw WarehouseManagementException.conflict("Bay code already exists for this side: " + normalizedCode);
                    });

            WarehouseBay bay = WarehouseBay.builder()
                    .side(side)
                    .code(normalizedCode)
                    .active(true)
                    .build();
            bay = bayRepository.save(bay);

            for (int levelNum = 1; levelNum <= levelsPerBay; levelNum++) {
                WarehouseBayLevel level = WarehouseBayLevel.builder()
                        .bay(bay)
                        .levelNum(levelNum)
                        .active(true)
                        .build();
                level = levelRepository.save(level);

                for (int shelfNum = 1; shelfNum <= shelvesPerLevel; shelfNum++) {
                    String layoutCode = side.getAisle().getLayout().getCode();
                    String aisleCode = side.getAisle().getCode();
                    String sideChar = side.getSide();

                    String locationCode = locationCodeGenerator.generate(
                            layoutCode, aisleCode, sideChar, normalizedCode, levelNum, shelfNum);

                    WarehouseShelf shelf = WarehouseShelf.builder()
                            .level(level)
                            .shelfNum(shelfNum)
                            .locationCode(locationCode)
                            .active(true)
                            .build();
                    shelfRepository.save(shelf);
                    locationCodes.add(locationCode);
                }
            }
        }

        return new BulkCreateResult(locationCodes);
    }

    private void cascadeRenameBayCode(UUID bayId, String oldCode, String newCode) {
        List<WarehouseShelf> shelves = shelfRepository.findAllByBayId(bayId);
        for (WarehouseShelf shelf : shelves) {
            String loc = shelf.getLocationCode();
            if (loc == null) {
                continue;
            }
            String[] parts = loc.split("-", -1);
            if (parts.length >= 6) {
                parts[3] = newCode;
                shelf.setLocationCode(String.join("-", parts));
            }
        }
        shelfRepository.saveAll(shelves);
    }

    private WarehouseAisleSide loadSide(UUID sideId) {
        return sideRepository.findById(sideId)
                .orElseThrow(() -> WarehouseManagementException.notFound("Side not found: " + sideId));
    }

    private WarehouseBay loadBay(UUID bayId) {
        return bayRepository.findById(bayId)
                .orElseThrow(() -> WarehouseManagementException.notFound("Bay not found: " + bayId));
    }

    private Specification<WarehouseBay> buildSpecification(UUID sideId, String search, Boolean active) {
        String normalizedSearch = normalizeSearch(search);
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(root.get("side").get("id"), sideId));

            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            if (normalizedSearch != null) {
                String value = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), value));
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

    private BayResult toResult(WarehouseBay bay) {
        return new BayResult(
                bay.getId(),
                bay.getSide().getId(),
                bay.getSide().getSide(),
                bay.getCode(),
                !Boolean.FALSE.equals(bay.getActive()),
                bay.getCreatedAt(),
                bay.getUpdatedAt(),
                bay.getDeactivatedAt());
    }

    public record BayResult(
            UUID id,
            UUID sideId,
            String side,
            String bayCode,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Instant deactivatedAt) {
    }

    public record BayPageResult(
            List<BayResult> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record BulkCreateResult(List<String> locationCodes) {
    }
}
