package com.warehouse.warehouse_platform.tenant.warehouse.block;

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
@Table(name = "block_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockTemplate {

    public enum IdentifierFormat {
        NUMERIC, ALPHA, CUSTOM, FREE_TEXT
    }

    public enum SideConfig {
        NONE, LR, AB, CUSTOM
    }

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_format", nullable = false, length = 20)
    private IdentifierFormat identifierFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "side_config", nullable = false, length = 20)
    @Builder.Default
    private SideConfig sideConfig = SideConfig.NONE;

    /**
     * Comma-separated option values used when sideConfig = CUSTOM.
     * Example: "North,South,East,West"
     */
    @Column(name = "side_options", length = 500)
    private String sideOptions;

    @Column(nullable = false)
    @Builder.Default
    private Boolean required = true;

    @Column(length = 500)
    private String description;

    @Column(name = "icon_name", length = 100)
    private String iconName;

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
        if (sideConfig == null)
            sideConfig = SideConfig.NONE;
        if (required == null)
            required = true;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
