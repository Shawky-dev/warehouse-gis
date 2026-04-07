package com.warehouse.warehouse_platform.tenant.gis.repository;

import com.warehouse.warehouse_platform.tenant.gis.model.GisBlock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

public interface WeightedHeatmapPointRepository extends Repository<GisBlock, UUID> {

    /**
     * Returns weighted GeoJSON-ready point data for the quantity_sum metric.
     * Sources leaf gis_blocks (locations that are not parents of other blocks),
     * joins v_stock on layout_block_id = location_id,
     * aggregates SUM(qty_stock) per location,
     * and excludes locations with non-positive total stock or null centroid.
     */
    @Query(nativeQuery = true, value = """
            SELECT
                b.layout_block_id  AS locationId,
                b.label            AS label,
                b.position_path    AS positionPath,
                ST_X(b.centroid_geom) AS lon,
                ST_Y(b.centroid_geom) AS lat,
                SUM(s.qty_stock)   AS weight
            FROM gis_blocks b
            JOIN v_stock s ON s.location_id = b.layout_block_id
            WHERE b.centroid_geom IS NOT NULL
              AND b.layout_block_id NOT IN (
                  SELECT DISTINCT lb.parent_id
                  FROM layout_blocks lb
                  WHERE lb.parent_id IS NOT NULL
              )
            GROUP BY b.layout_block_id, b.label, b.position_path, b.centroid_geom
            HAVING SUM(s.qty_stock) > 0
            """)
    List<WeightedPointProjection> findQuantitySumPoints();
}
