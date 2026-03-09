package com.warehouse.warehouse_platform.tenant.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID>, JpaSpecificationExecutor<Supplier> {

    @Query("select s from Supplier s where lower(s.code) = lower(:code)")
    Optional<Supplier> findByCodeIgnoreCase(String code);
}
