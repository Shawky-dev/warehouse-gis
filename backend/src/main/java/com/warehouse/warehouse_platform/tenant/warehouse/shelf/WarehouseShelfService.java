package com.warehouse.warehouse_platform.tenant.warehouse.shelf;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.aisle.WarehouseAisle;
import com.warehouse.warehouse_platform.tenant.warehouse.bay.WarehouseBay;
import com.warehouse.warehouse_platform.tenant.warehouse.code.WarehouseLocationCodeGenerator;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayout;
import com.warehouse.warehouse_platform.tenant.warehouse.level.WarehouseBayLevel;
import com.warehouse.warehouse_platform.tenant.warehouse.level.WarehouseBayLevelRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.side.WarehouseAisleSide;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WarehouseShelfService {

    private final WarehouseBayLevelRepository levelRepository;
    private final WarehouseShelfRepository shelfRepository;
    private final WarehouseLocationCodeGenerator locationCodeGenerator;
    private final TenantAuditService tenantAuditService;

    public WarehouseShelfService(
            WarehouseBayLevelRepository levelRepository,
            WarehouseShelfRepository shelfRepository,
            WarehouseLocationCodeGenerator locationCodeGenerator,
            TenantAuditService tenantAuditService) {
        this.levelRepository = levelRepository;
        this.shelfRepository = shelfRepository;
        this.locationCodeGenerator = locationCodeGenerator;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public ShelfListResult listShelves(UUID levelId, Boolean active) {
        loadLevel(levelId);
        List<WarehouseShelf> shelves = shelfRepository.findAll(
                (root, query, cb) -> {
                    java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
                    predicates.add(cb.equal(root.get("level").get("id"), levelId));
                    if (active != null) {
                        predicates.add(cb.equal(root.get("active"), active));
                    }
                    return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
                });
        return new ShelfListResult(shelves.stream().map(this::toResult).toList());
    }

    @Transactional(readOnly = true)
    public ShelfListResult listShelvesGlobal(UUID levelId, Boolean active) {
        List<WarehouseShelf> shelves = shelfRepository.findAll(
                (root, query, cb) -> {
                    java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
                    if (levelId != null) {
                        predicates.add(cb.equal(root.get("level").get("id"), levelId));
                    }
                    if (active != null) {
                        predicates.add(cb.equal(root.get("active"), active));
                    }
                    return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
                });
        return new ShelfListResult(shelves.stream().map(this::toResult).toList());
    }

    @Transactional(readOnly = true)
    public ShelfResult getShelf(UUID shelfId) {
        return toResult(loadShelf(shelfId));
    }

    @Transactional
    public ShelfResult createShelf(UUID levelId, int shelfNum) {
        if (shelfNum < 1) {
            throw WarehouseManagementException.badRequest("shelfNum must be >= 1");
        }

        WarehouseBayLevel level = loadLevel(levelId);

        shelfRepository.findByLevel_IdAndShelfNum(levelId, shelfNum)
                .ifPresent(existing -> {
                    throw WarehouseManagementException.conflict("Shelf " + shelfNum + " already exists for this level");
                });

        // Traverse hierarchy to build location code
        WarehouseBay bay = level.getBay();
        WarehouseAisleSide side = bay.getSide();
        WarehouseAisle aisle = side.getAisle();
        WarehouseLayout layout = aisle.getLayout();

        String locationCode = locationCodeGenerator.generate(
                layout.getCode(),
                aisle.getCode(),
                side.getSide(),
                bay.getCode(),
                level.getLevelNum(),
                shelfNum);

        WarehouseShelf shelf = WarehouseShelf.builder()
                .level(level)
                .shelfNum(shelfNum)
                .locationCode(locationCode)
                .active(true)
                .build();

        WarehouseShelf saved = shelfRepository.save(shelf);
        ShelfResult result = toResult(saved);
        tenantAuditService.record("WAREHOUSE_SHELF_CREATE", "WAREHOUSE_SHELF", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public void softDeleteShelf(UUID shelfId) {
        WarehouseShelf shelf = loadShelf(shelfId);
        ShelfResult before = toResult(shelf);

        if (!Boolean.FALSE.equals(shelf.getActive())) {
            shelf.setActive(false);
            shelf.setDeactivatedAt(Instant.now());
            shelfRepository.save(shelf);
        }

        tenantAuditService.record("WAREHOUSE_SHELF_SOFT_DELETE", "WAREHOUSE_SHELF", shelfId.toString(), before, toResult(shelf));
    }

    @Transactional
    public void restoreShelf(UUID shelfId) {
        WarehouseShelf shelf = loadShelf(shelfId);
        ShelfResult before = toResult(shelf);

        if (!Boolean.TRUE.equals(shelf.getActive()) || shelf.getDeactivatedAt() != null) {
            shelf.setActive(true);
            shelf.setDeactivatedAt(null);
            shelfRepository.save(shelf);
        }

        tenantAuditService.record("WAREHOUSE_SHELF_RESTORE", "WAREHOUSE_SHELF", shelfId.toString(), before, toResult(shelf));
    }

    @Transactional
    public void hardDeleteShelf(UUID shelfId) {
        WarehouseShelf shelf = loadShelf(shelfId);

        if (!Boolean.FALSE.equals(shelf.getActive())) {
            throw WarehouseManagementException.forbidden("Shelf must be inactive before hard delete");
        }

        ShelfResult before = toResult(shelf);
        shelfRepository.delete(shelf);
        tenantAuditService.record("WAREHOUSE_SHELF_HARD_DELETE", "WAREHOUSE_SHELF", shelfId.toString(), before, null);
    }

    private WarehouseBayLevel loadLevel(UUID levelId) {
        return levelRepository.findById(levelId)
                .orElseThrow(() -> WarehouseManagementException.notFound("Level not found: " + levelId));
    }

    private WarehouseShelf loadShelf(UUID shelfId) {
        return shelfRepository.findById(shelfId)
                .orElseThrow(() -> WarehouseManagementException.notFound("Shelf not found: " + shelfId));
    }

    private ShelfResult toResult(WarehouseShelf shelf) {
        return new ShelfResult(
                shelf.getId(),
                shelf.getLevel().getId(),
                shelf.getShelfNum(),
                shelf.getLocationCode(),
                !Boolean.FALSE.equals(shelf.getActive()),
                shelf.getCreatedAt(),
                shelf.getUpdatedAt(),
                shelf.getDeactivatedAt());
    }

    public record ShelfResult(
            UUID id,
            UUID levelId,
            int shelfNum,
            String locationCode,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Instant deactivatedAt) {
    }

    public record ShelfListResult(List<ShelfResult> content) {
    }
}
