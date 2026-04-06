package com.warehouse.warehouse_platform.tenant.gis.model;

import com.warehouse.warehouse_platform.tenant.zonetype.ZoneType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
@Table(name = "gis_zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GisZone {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "GEOMETRY(Polygon, 4326)", nullable = false)
    private Polygon geometry;

    /** BLOCK | WARN */
    @Column(name = "violation_action", nullable = false, length = 20)
    private String violationAction;

    /** MANUAL | ARCGIS_IMPORT */
    @Column(nullable = false, length = 20)
    private String source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_type_id")
    private ZoneType zoneType;

    /** Hex color in #RRGGBB format. Metadata only; not used in validation. */
    @Column(name = "display_color", length = 7)
    private String displayColor;

    @Builder.Default
    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GisZoneCategoryRule> categoryRules = new ArrayList<>();

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
        if (violationAction == null)
            violationAction = "BLOCK";
        if (source == null)
            source = "MANUAL";
        if (displayColor == null)
            displayColor = "#6B7280";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
