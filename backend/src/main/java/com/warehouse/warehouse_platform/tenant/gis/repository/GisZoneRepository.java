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

    long countByZoneType_Id(UUID zoneTypeId);

    List<GisZone> findByZoneType_Id(UUID zoneTypeId);

    /**
     * Returns true when at least one zone with the given zoneTypeId spatially
     * contains the block for the given location.
     */
    @Query(value = """
            SELECT COUNT(*) > 0 FROM gis_zones z
            WHERE z.zone_type_id = :zoneTypeId
              AND ST_Contains(z.geometry,
                  (SELECT geometry FROM gis_blocks WHERE layout_block_id = :layoutBlockId LIMIT 1))
            """, nativeQuery = true)
    boolean existsZoneOfTypeContainingLocation(
            @Param("layoutBlockId") UUID layoutBlockId,
            @Param("zoneTypeId") UUID zoneTypeId);
}
