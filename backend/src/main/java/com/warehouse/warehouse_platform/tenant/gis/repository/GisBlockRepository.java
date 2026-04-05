package com.warehouse.warehouse_platform.tenant.gis.repository;

import com.warehouse.warehouse_platform.tenant.gis.model.GisBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface GisBlockRepository extends JpaRepository<GisBlock, UUID> {

    @Query(value = "SELECT DISTINCT template_name FROM gis_blocks", nativeQuery = true)
    List<String> findDistinctTemplateNames();

    @Query(value = "SELECT MIN(depth) FROM gis_blocks WHERE template_name = :templateName", nativeQuery = true)
    Integer findMinDepthByTemplateName(@Param("templateName") String templateName);

    List<GisBlock> findAllByTemplateNameOrderByDepthAsc(String templateName);

    Optional<GisBlock> findByLayoutBlockId(UUID layoutBlockId);

    @Query(value = """
            SELECT z.* FROM gis_blocks z
            WHERE z.template_name = 'Zone'
              AND ST_Contains(z.geometry,
                  (SELECT geometry FROM gis_blocks WHERE layout_block_id = :layoutBlockId))
            LIMIT 1
            """, nativeQuery = true)
    Optional<GisBlock> findContainingZone(@Param("layoutBlockId") UUID layoutBlockId);

    @Query(value = """
            SELECT * FROM gis_blocks
            WHERE template_name = 'Zone'
              AND :categoryId = ANY(allowed_category_ids)
            """, nativeQuery = true)
    List<GisBlock> findZonesAllowingCategory(@Param("categoryId") UUID categoryId);

    @Query(value = """
            SELECT b.* FROM gis_blocks b
            WHERE b.layout_block_id NOT IN (
                SELECT DISTINCT lb.parent_id
                FROM layout_blocks lb
                WHERE lb.parent_id IS NOT NULL
            )
            """, nativeQuery = true)
    List<GisBlock> findLeafGisBlocks();
}
