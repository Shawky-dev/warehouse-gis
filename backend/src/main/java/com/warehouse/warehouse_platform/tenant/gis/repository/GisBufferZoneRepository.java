package com.warehouse.warehouse_platform.tenant.gis.repository;

import com.warehouse.warehouse_platform.tenant.gis.model.GisBufferZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GisBufferZoneRepository extends JpaRepository<GisBufferZone, UUID> {

    @Query(value = """
            SELECT bz.* FROM gis_buffer_zones bz
            WHERE ST_Intersects(bz.geometry,
                (SELECT geometry FROM gis_blocks WHERE layout_block_id = :layoutBlockId))
            """, nativeQuery = true)
    List<GisBufferZone> findIntersectingBufferZones(@Param("layoutBlockId") UUID layoutBlockId);
}
