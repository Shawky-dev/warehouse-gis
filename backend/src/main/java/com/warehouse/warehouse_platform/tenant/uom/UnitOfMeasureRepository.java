package com.warehouse.warehouse_platform.tenant.uom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, UUID>, JpaSpecificationExecutor<UnitOfMeasure> {

    @Query("select u from UnitOfMeasure u where lower(u.code) = lower(:code)")
    Optional<UnitOfMeasure> findByCodeIgnoreCase(String code);
}
