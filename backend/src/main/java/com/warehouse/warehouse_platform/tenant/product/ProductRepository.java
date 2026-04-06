package com.warehouse.warehouse_platform.tenant.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    @Query("select p from Product p where lower(p.sku) = lower(:sku)")
    Optional<Product> findBySkuIgnoreCase(String sku);

    long countByBaseUom_Id(UUID baseUomId);

    long countByCategory_Id(UUID categoryId);

    long countByHazardType_Id(UUID hazardTypeId);
}
