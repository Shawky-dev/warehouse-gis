package com.warehouse.warehouse_platform.tenant.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID>, JpaSpecificationExecutor<ProductCategory> {

    @Query("select c from ProductCategory c where lower(c.name) = lower(:name)")
    Optional<ProductCategory> findByNameIgnoreCase(String name);

    @Query("select c from ProductCategory c where lower(c.code) = lower(:code)")
    Optional<ProductCategory> findByCodeIgnoreCase(String code);

    @Query("select case when count(c) > 0 then true else false end from ProductCategory c where lower(c.code) = lower(:code) and c.id <> :excludeId")
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID excludeId);

    long countByRequiredZoneType_Id(UUID zoneTypeId);
}
