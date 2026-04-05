package com.warehouse.warehouse_platform.tenant.gis.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gis_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GisBlock {

    @Id
    private UUID id;

    @Column(name = "layout_block_id", nullable = false)
    private UUID layoutBlockId;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(length = 200)
    private String label;

    @Column(name = "position_path")
    private String positionPath;

    @Column(name = "depth", nullable = false)
    private int depth;

    @Column(columnDefinition = "GEOMETRY(Polygon, 4326)", nullable = false)
    private Polygon geometry;

    @Column(name = "centroid_geom", columnDefinition = "GEOMETRY(Point, 4326)")
    private Point centroidGeom;

    @Column(name = "zone_type", length = 50)
    private String zoneType;

    @Column(name = "allowed_category_ids", columnDefinition = "uuid[]")
    private UUID[] allowedCategoryIds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
