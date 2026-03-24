package com.warehouse.warehouse_platform.tenant.gis.repository;

import com.warehouse.warehouse_platform.tenant.gis.model.GisBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;


public interface GisBlockRepository extends JpaRepository<GisBlock, UUID> {

    void deleteAllByLayoutBlockIdIn(Collection<UUID> layoutBlockIds);

    @Query(value = "SELECT DISTINCT template_name FROM gis_blocks", nativeQuery = true)
    List<String> findDistinctTemplateNames();

    @Query(value = "SELECT MIN(depth) FROM gis_blocks WHERE template_name = :templateName", nativeQuery = true)
    Integer findMinDepthByTemplateName(@Param("templateName") String templateName);

    List<GisBlock> findAllByTemplateNameOrderByDepthAsc(String templateName);
}
