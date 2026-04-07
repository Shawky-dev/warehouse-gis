package com.warehouse.warehouse_platform.tenant.gis.repository;

import com.warehouse.warehouse_platform.tenant.gis.model.PublishStatus;
import com.warehouse.warehouse_platform.tenant.gis.model.StaticHeatmap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaticHeatmapRepository extends JpaRepository<StaticHeatmap, UUID> {

    List<StaticHeatmap> findAllByPublishStatusOrderByCreatedAtDesc(PublishStatus publishStatus);

    Optional<StaticHeatmap> findByPublishStatusAndIsDefaultTrue(PublishStatus publishStatus);

    Optional<StaticHeatmap> findTop1ByPublishStatusAndIdNotOrderByCreatedAtDesc(
            PublishStatus publishStatus, UUID id);

    long countByPublishStatus(PublishStatus publishStatus);

    @Query("SELECT h FROM StaticHeatmap h WHERE h.geoserverLayerName = :layerName AND h.publishStatus = :status")
    Optional<StaticHeatmap> findByGeoserverLayerNameAndPublishStatus(
            @Param("layerName") String layerName,
            @Param("status") PublishStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE StaticHeatmap h SET h.isDefault = false WHERE h.publishStatus = :status")
    void clearAllDefaults(@Param("status") PublishStatus status);
}
