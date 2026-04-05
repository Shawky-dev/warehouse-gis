package com.warehouse.warehouse_platform.tenant.gis.repository;

import com.warehouse.warehouse_platform.tenant.gis.model.GisZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GisZoneRepository extends JpaRepository<GisZone, UUID> {

    @Query(value = """
            SELECT z.* FROM gis_zones z
            WHERE ST_Contains(z.geometry,
                (SELECT geometry FROM gis_blocks WHERE layout_block_id = :layoutBlockId))
            """, nativeQuery = true)
    List<GisZone> findZonesContainingLocation(@Param("layoutBlockId") UUID layoutBlockId);

    List<GisZone> findAllByOrderByCreatedAtAsc();
}
