package com.warehouse.warehouse_platform.tenant.gis.model;

import com.warehouse.warehouse_platform.tenant.hazardtype.HazardType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Polygon;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "gis_hazard_buffers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GisHazardBuffer {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(columnDefinition = "GEOMETRY(Polygon, 4326)", nullable = false)
    private Polygon geometry;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "import_batch_id")
    private UUID importBatchId;

    @Column(name = "source_filename", length = 255)
    private String sourceFilename;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Builder.Default
    @ManyToMany
    @JoinTable(name = "gis_hazard_buffer_restricted_hazard_types", joinColumns = @JoinColumn(name = "hazard_buffer_id"), inverseJoinColumns = @JoinColumn(name = "hazard_type_id"))
    private List<HazardType> restrictedHazardTypes = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null)
            id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (importedAt == null)
            importedAt = now;
        if (source == null)
            source = "ARCGIS_IMPORT";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
