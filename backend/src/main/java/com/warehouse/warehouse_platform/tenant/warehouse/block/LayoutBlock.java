package com.warehouse.warehouse_platform.tenant.warehouse.block;

import com.warehouse.warehouse_platform.tenant.warehouse.locationkind.WarehouseLocationKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/**
 * A node in the ordered, nested tree of block templates that defines
 * the physical structure of a warehouse layout.
 *
 * parentId = NULL means this is a root-level block (top of the hierarchy).
 * position is the ordering index within the same parent (0-based).
 */
@Entity
@Table(name = "layout_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LayoutBlock {

    @Id
    private UUID id;

    @Column(name = "layout_id", nullable = false)
    private UUID layoutId;

    @Column(name = "block_template_id", nullable = false)
    private UUID blockTemplateId;

    /** NULL = root level */
    @Column(name = "parent_id")
    private UUID parentId;

    /** 0-based ordering index within the same parent */
    @Column(nullable = false)
    private Integer position;

    @Column(length = 50)
    private String side;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_kind_id", nullable = false)
    private WarehouseLocationKind locationKind;

    @Column(name = "scan_code", unique = true, length = 60)
    private String scanCode;

    @Column(name = "full_code", length = 200)
    private String fullCode;

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
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
