package com.warehouse.warehouse_platform.tenant.warehouse.side;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.aisle.WarehouseAisle;
import com.warehouse.warehouse_platform.tenant.warehouse.aisle.WarehouseAisleRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.bay.WarehouseBayRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WarehouseAisleSideService {

    private final WarehouseAisleRepository aisleRepository;
    private final WarehouseAisleSideRepository sideRepository;
    private final WarehouseBayRepository bayRepository;
    private final TenantAuditService tenantAuditService;

    public WarehouseAisleSideService(
            WarehouseAisleRepository aisleRepository,
            WarehouseAisleSideRepository sideRepository,
            WarehouseBayRepository bayRepository,
            TenantAuditService tenantAuditService) {
        this.aisleRepository = aisleRepository;
        this.sideRepository = sideRepository;
        this.bayRepository = bayRepository;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public SideListResult listSides(UUID aisleId, Boolean active) {
        loadAisle(aisleId);
        List<WarehouseAisleSide> sides = sideRepository.findAllByAisle_Id(aisleId);
        List<SideResult> content = sides.stream()
                .filter(s -> active == null || active.equals(s.getActive()))
                .map(this::toResult)
                .toList();
        return new SideListResult(content);
    }

    @Transactional(readOnly = true)
    public SideResult getSide(UUID sideId) {
        return toResult(loadSide(sideId));
    }

    @Transactional
    public SideResult createSide(UUID aisleId, String side) {
        if (side == null || (!side.equals("L") && !side.equals("R"))) {
            throw WarehouseManagementException.badRequest("side must be 'L' or 'R'");
        }

        WarehouseAisle aisle = loadAisle(aisleId);

        sideRepository.findByAisle_IdAndSide(aisleId, side)
                .ifPresent(existing -> {
                    throw WarehouseManagementException.conflict("Side '" + side + "' already exists for this aisle");
                });

        WarehouseAisleSide entity = WarehouseAisleSide.builder()
                .aisle(aisle)
                .side(side)
                .active(true)
                .build();

        WarehouseAisleSide saved = sideRepository.save(entity);
        SideResult result = toResult(saved);
        tenantAuditService.record("WAREHOUSE_SIDE_CREATE", "WAREHOUSE_SIDE", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public void softDeleteSide(UUID sideId) {
        WarehouseAisleSide side = loadSide(sideId);
        SideResult before = toResult(side);

        if (!Boolean.FALSE.equals(side.getActive())) {
            side.setActive(false);
            side.setDeactivatedAt(Instant.now());
            sideRepository.save(side);
        }

        tenantAuditService.record("WAREHOUSE_SIDE_SOFT_DELETE", "WAREHOUSE_SIDE", sideId.toString(), before, toResult(side));
    }

    @Transactional
    public void restoreSide(UUID sideId) {
        WarehouseAisleSide side = loadSide(sideId);
        SideResult before = toResult(side);

        if (!Boolean.TRUE.equals(side.getActive()) || side.getDeactivatedAt() != null) {
            side.setActive(true);
            side.setDeactivatedAt(null);
            sideRepository.save(side);
        }

        tenantAuditService.record("WAREHOUSE_SIDE_RESTORE", "WAREHOUSE_SIDE", sideId.toString(), before, toResult(side));
    }

    @Transactional
    public void hardDeleteSide(UUID sideId) {
        WarehouseAisleSide side = loadSide(sideId);

        if (!Boolean.FALSE.equals(side.getActive())) {
            throw WarehouseManagementException.forbidden("Side must be inactive before hard delete");
        }

        long bayCount = bayRepository.countBySide_Id(sideId);
        if (bayCount > 0) {
            throw WarehouseManagementException.conflict("Side cannot be hard deleted while it has bays");
        }

        SideResult before = toResult(side);
        sideRepository.delete(side);
        tenantAuditService.record("WAREHOUSE_SIDE_HARD_DELETE", "WAREHOUSE_SIDE", sideId.toString(), before, null);
    }

    private WarehouseAisle loadAisle(UUID aisleId) {
        return aisleRepository.findById(aisleId)
                .orElseThrow(() -> WarehouseManagementException.notFound("Aisle not found: " + aisleId));
    }

    private WarehouseAisleSide loadSide(UUID sideId) {
        return sideRepository.findById(sideId)
                .orElseThrow(() -> WarehouseManagementException.notFound("Side not found: " + sideId));
    }

    private SideResult toResult(WarehouseAisleSide side) {
        WarehouseAisle aisle = side.getAisle();
        return new SideResult(
                side.getId(),
                aisle.getId(),
                aisle.getCode(),
                aisle.getLayout().getCode(),
                side.getSide(),
                !Boolean.FALSE.equals(side.getActive()),
                side.getCreatedAt(),
                side.getUpdatedAt(),
                side.getDeactivatedAt());
    }

    public record SideResult(
            UUID id,
            UUID aisleId,
            String aisleCode,
            String layoutCode,
            String side,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Instant deactivatedAt) {
    }

    public record SideListResult(List<SideResult> content) {
    }
}
