package com.warehouse.warehouse_platform.tenant.warehouse.shelf;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseShelfRepository extends JpaRepository<WarehouseShelf, UUID>, JpaSpecificationExecutor<WarehouseShelf> {

    Optional<WarehouseShelf> findByLevel_IdAndShelfNum(UUID levelId, int shelfNum);

    long countByLevel_Id(UUID levelId);

    @Query("SELECT s FROM WarehouseShelf s JOIN s.level l JOIN l.bay b JOIN b.side si JOIN si.aisle a WHERE a.layout.id = :layoutId")
    List<WarehouseShelf> findAllByLayoutId(@Param("layoutId") UUID layoutId);

    @Query("SELECT s FROM WarehouseShelf s JOIN s.level l JOIN l.bay b JOIN b.side si WHERE si.aisle.id = :aisleId")
    List<WarehouseShelf> findAllByAisleId(@Param("aisleId") UUID aisleId);

    @Query("SELECT s FROM WarehouseShelf s JOIN s.level l JOIN l.bay b WHERE b.side.id = :sideId")
    List<WarehouseShelf> findAllBySideId(@Param("sideId") UUID sideId);

    @Query("SELECT s FROM WarehouseShelf s JOIN s.level l WHERE l.bay.id = :bayId")
    List<WarehouseShelf> findAllByBayId(@Param("bayId") UUID bayId);
}
