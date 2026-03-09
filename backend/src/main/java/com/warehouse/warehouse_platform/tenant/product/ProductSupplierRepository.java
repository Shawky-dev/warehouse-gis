package com.warehouse.warehouse_platform.tenant.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductSupplierRepository extends JpaRepository<ProductSupplier, ProductSupplierId> {

    List<ProductSupplier> findAllByProduct_Id(UUID productId);

    List<ProductSupplier> findAllByProduct_IdIn(Collection<UUID> productIds);

    long countByProduct_Id(UUID productId);

    long countBySupplier_Id(UUID supplierId);

    void deleteByProduct_Id(UUID productId);
}
