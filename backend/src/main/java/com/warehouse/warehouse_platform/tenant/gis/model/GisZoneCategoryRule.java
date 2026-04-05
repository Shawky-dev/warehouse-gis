package com.warehouse.warehouse_platform.tenant.gis.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "gis_zone_category_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GisZoneCategoryRule {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private GisZone zone;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    /** ALLOWED | PROHIBITED */
    @Column(name = "rule_type", nullable = false, length = 20)
    private String ruleType;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
