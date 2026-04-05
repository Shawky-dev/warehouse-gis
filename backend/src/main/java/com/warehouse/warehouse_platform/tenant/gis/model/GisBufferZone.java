package com.warehouse.warehouse_platform.tenant.gis.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gis_buffer_zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GisBufferZone {

    @Id
    private UUID id;

    @Column(length = 200)
    private String label;

    @Column(name = "material_type", nullable = false, length = 100)
    private String materialType;

    @Column(name = "buffer_distance_m", precision = 8, scale = 2)
    private BigDecimal bufferDistanceM;

    @Column(columnDefinition = "GEOMETRY(Polygon, 4326)", nullable = false)
    private Polygon geometry;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null)
            id = UUID.randomUUID();
        createdAt = Instant.now();
    }
}
