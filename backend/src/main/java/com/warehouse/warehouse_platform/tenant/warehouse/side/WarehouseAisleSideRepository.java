package com.warehouse.warehouse_platform.tenant.warehouse.side;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseAisleSideRepository extends JpaRepository<WarehouseAisleSide, UUID>, JpaSpecificationExecutor<WarehouseAisleSide> {

    Optional<WarehouseAisleSide> findByAisle_IdAndSide(UUID aisleId, String side);

    long countByAisle_Id(UUID aisleId);

    List<WarehouseAisleSide> findAllByAisle_Id(UUID aisleId);
}
