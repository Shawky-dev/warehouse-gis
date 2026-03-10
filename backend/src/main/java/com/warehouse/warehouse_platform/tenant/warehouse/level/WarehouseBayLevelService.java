package com.warehouse.warehouse_platform.tenant.warehouse.level;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.bay.WarehouseBay;
import com.warehouse.warehouse_platform.tenant.warehouse.bay.WarehouseBayRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import com.warehouse.warehouse_platform.tenant.warehouse.shelf.WarehouseShelfRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WarehouseBayLevelService {

    private final WarehouseBayRepository bayRepository;
    private final WarehouseBayLevelRepository levelRepository;
    private final WarehouseShelfRepository shelfRepository;
    private final TenantAuditService tenantAuditService;

    public WarehouseBayLevelService(
            WarehouseBayRepository bayRepository,
            WarehouseBayLevelRepository levelRepository,
            WarehouseShelfRepository shelfRepository,
            TenantAuditService tenantAuditService) {
        this.bayRepository = bayRepository;
        this.levelRepository = levelRepository;
        this.shelfRepository = shelfRepository;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public LevelListResult listLevels(UUID bayId, Boolean active) {
        loadBay(bayId);
        List<WarehouseBayLevel> levels = levelRepository.findAll(
                (root, query, cb) -> {
                    java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
                    predicates.add(cb.equal(root.get("bay").get("id"), bayId));
                    if (active != null) {
                        predicates.add(cb.equal(root.get("active"), active));
                    }
                    return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
                });
        return new LevelListResult(levels.stream().map(this::toResult).toList());
    }

    @Transactional(readOnly = true)
    public LevelResult getLevel(UUID levelId) {
        return toResult(loadLevel(levelId));
    }

    @Transactional
    public LevelResult createLevel(UUID bayId, int levelNum) {
        if (levelNum < 1) {
            throw WarehouseManagementException.badRequest("levelNum must be >= 1");
        }

        WarehouseBay bay = loadBay(bayId);

        levelRepository.findByBay_IdAndLevelNum(bayId, levelNum)
                .ifPresent(existing -> {
                    throw WarehouseManagementException.conflict("Level " + levelNum + " already exists for this bay");
                });

        WarehouseBayLevel level = WarehouseBayLevel.builder()
                .bay(bay)
                .levelNum(levelNum)
                .active(true)
                .build();

        WarehouseBayLevel saved = levelRepository.save(level);
        LevelResult result = toResult(saved);
        tenantAuditService.record("WAREHOUSE_LEVEL_CREATE", "WAREHOUSE_LEVEL", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public void softDeleteLevel(UUID levelId) {
        WarehouseBayLevel level = loadLevel(levelId);
        LevelResult before = toResult(level);

        if (!Boolean.FALSE.equals(level.getActive())) {
            level.setActive(false);
            level.setDeactivatedAt(Instant.now());
            levelRepository.save(level);
        }

        tenantAuditService.record("WAREHOUSE_LEVEL_SOFT_DELETE", "WAREHOUSE_LEVEL", levelId.toString(), before, toResult(level));
    }

    @Transactional
    public void restoreLevel(UUID levelId) {
        WarehouseBayLevel level = loadLevel(levelId);
        LevelResult before = toResult(level);

        if (!Boolean.TRUE.equals(level.getActive()) || level.getDeactivatedAt() != null) {
            level.setActive(true);
            level.setDeactivatedAt(null);
            levelRepository.save(level);
        }

        tenantAuditService.record("WAREHOUSE_LEVEL_RESTORE", "WAREHOUSE_LEVEL", levelId.toString(), before, toResult(level));
    }

    @Transactional
    public void hardDeleteLevel(UUID levelId) {
        WarehouseBayLevel level = loadLevel(levelId);

        if (!Boolean.FALSE.equals(level.getActive())) {
            throw WarehouseManagementException.forbidden("Level must be inactive before hard delete");
        }

        long shelfCount = shelfRepository.countByLevel_Id(levelId);
        if (shelfCount > 0) {
            throw WarehouseManagementException.conflict("Level cannot be hard deleted while it has shelves");
        }

        LevelResult before = toResult(level);
        levelRepository.delete(level);
        tenantAuditService.record("WAREHOUSE_LEVEL_HARD_DELETE", "WAREHOUSE_LEVEL", levelId.toString(), before, null);
    }

    private WarehouseBay loadBay(UUID bayId) {
        return bayRepository.findById(bayId)
                .orElseThrow(() -> WarehouseManagementException.notFound("Bay not found: " + bayId));
    }

    private WarehouseBayLevel loadLevel(UUID levelId) {
        return levelRepository.findById(levelId)
                .orElseThrow(() -> WarehouseManagementException.notFound("Level not found: " + levelId));
    }

    private LevelResult toResult(WarehouseBayLevel level) {
        return new LevelResult(
                level.getId(),
                level.getBay().getId(),
                level.getBay().getCode(),
                level.getLevelNum(),
                !Boolean.FALSE.equals(level.getActive()),
                level.getCreatedAt(),
                level.getUpdatedAt(),
                level.getDeactivatedAt());
    }

    public record LevelResult(
            UUID id,
            UUID bayId,
            String bayCode,
            int levelNum,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Instant deactivatedAt) {
    }

    public record LevelListResult(List<LevelResult> content) {
    }
}
