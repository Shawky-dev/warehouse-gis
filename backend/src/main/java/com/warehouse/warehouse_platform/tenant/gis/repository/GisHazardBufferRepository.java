package com.warehouse.warehouse_platform.tenant.gis.repository;

import com.warehouse.warehouse_platform.tenant.gis.model.GisHazardBuffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GisHazardBufferRepository extends JpaRepository<GisHazardBuffer, UUID> {

    List<GisHazardBuffer> findAllByOrderByNameAscIdAsc();

    /**
     * Returns hazard buffers whose geometry intersects the centroid of the given
     * location (layout block), filtered to those that restrict the given hazard
     * type. Ordered deterministically: name ASC, id ASC.
     */
    @Query(value = """
            SELECT hb.* FROM gis_hazard_buffers hb
            JOIN gis_hazard_buffer_restricted_hazard_types rht
              ON rht.hazard_buffer_id = hb.id AND rht.hazard_type_id = :hazardTypeId
            WHERE ST_Intersects(hb.geometry,
                (SELECT geometry FROM gis_blocks WHERE layout_block_id = :layoutBlockId LIMIT 1))
            ORDER BY hb.name ASC, hb.id ASC
            """, nativeQuery = true)
    List<GisHazardBuffer> findMatchingBuffersForLocation(
            @Param("layoutBlockId") UUID layoutBlockId,
            @Param("hazardTypeId") UUID hazardTypeId);
}
