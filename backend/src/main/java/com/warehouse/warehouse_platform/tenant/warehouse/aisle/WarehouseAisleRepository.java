package com.warehouse.warehouse_platform.tenant.warehouse.aisle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseAisleRepository extends JpaRepository<WarehouseAisle, UUID>, JpaSpecificationExecutor<WarehouseAisle> {

    Optional<WarehouseAisle> findByLayout_IdAndCodeIgnoreCase(UUID layoutId, String code);

    long countByLayout_Id(UUID layoutId);

    List<WarehouseAisle> findAllByLayout_Id(UUID layoutId);
}
