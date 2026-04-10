package com.warehouse.warehouse_platform.tenant.gis.repository;

import com.warehouse.warehouse_platform.tenant.gis.model.GisDataLayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GisDataLayerRepository extends JpaRepository<GisDataLayer, UUID> {

    List<GisDataLayer> findAllByOrderByNameAscIdAsc();
}
