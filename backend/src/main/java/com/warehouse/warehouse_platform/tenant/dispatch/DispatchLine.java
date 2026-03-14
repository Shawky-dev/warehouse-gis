package com.warehouse.warehouse_platform.tenant.dispatch;

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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "dispatch_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispatchLine {

    @Id
    private UUID id;

    @Column(name = "dispatch_id", nullable = false)
    private UUID dispatchId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "source_location_id", nullable = false)
    private UUID sourceLocationId;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal qty;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private Integer position = 0;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (position == null) {
            position = 0;
        }
    }
}
