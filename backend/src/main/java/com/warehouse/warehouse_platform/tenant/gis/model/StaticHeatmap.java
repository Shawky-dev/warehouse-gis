package com.warehouse.warehouse_platform.tenant.gis.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gis_static_heatmaps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaticHeatmap {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "source_filename", nullable = false, length = 255)
    private String sourceFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "geoserver_coverage_store", nullable = false, length = 160, unique = true)
    private String geoserverCoverageStore;

    @Column(name = "geoserver_layer_name", nullable = false, length = 160, unique = true)
    private String geoserverLayerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_status", nullable = false, length = 20)
    private PublishStatus publishStatus;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "uploaded_by", nullable = false, length = 255)
    private String uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
