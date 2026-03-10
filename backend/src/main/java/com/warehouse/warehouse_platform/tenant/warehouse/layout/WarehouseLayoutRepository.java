package com.warehouse.warehouse_platform.tenant.warehouse.layout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseLayoutRepository extends JpaRepository<WarehouseLayout, UUID>, JpaSpecificationExecutor<WarehouseLayout> {

    Optional<WarehouseLayout> findByCodeIgnoreCase(String code);
}
