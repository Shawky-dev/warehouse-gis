package com.warehouse.warehouse_platform.tenant.warehouse.locationkind;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseLocationKindRepository extends JpaRepository<WarehouseLocationKind, UUID> {

    @Query("select k from WarehouseLocationKind k order by k.sortOrder asc, lower(k.name) asc")
    List<WarehouseLocationKind> findAllOrdered();

    @Query("select k from WarehouseLocationKind k where lower(k.name) = lower(:name)")
    Optional<WarehouseLocationKind> findByNameIgnoreCase(String name);

    Optional<WarehouseLocationKind> findFirstByOrderBySortOrderAscIdAsc();

    @Query("select coalesce(max(k.sortOrder), -1) from WarehouseLocationKind k")
    int findMaxSortOrder();
}
