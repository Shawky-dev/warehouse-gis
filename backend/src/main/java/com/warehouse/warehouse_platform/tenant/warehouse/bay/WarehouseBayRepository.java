package com.warehouse.warehouse_platform.tenant.warehouse.bay;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseBayRepository extends JpaRepository<WarehouseBay, UUID>, JpaSpecificationExecutor<WarehouseBay> {

    Optional<WarehouseBay> findBySide_IdAndCodeIgnoreCase(UUID sideId, String code);

    long countBySide_Id(UUID sideId);
}
