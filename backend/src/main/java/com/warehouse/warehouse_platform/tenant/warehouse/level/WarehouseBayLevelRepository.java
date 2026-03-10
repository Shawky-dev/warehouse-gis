package com.warehouse.warehouse_platform.tenant.warehouse.level;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseBayLevelRepository extends JpaRepository<WarehouseBayLevel, UUID>, JpaSpecificationExecutor<WarehouseBayLevel> {

    Optional<WarehouseBayLevel> findByBay_IdAndLevelNum(UUID bayId, int levelNum);

    long countByBay_Id(UUID bayId);
}
