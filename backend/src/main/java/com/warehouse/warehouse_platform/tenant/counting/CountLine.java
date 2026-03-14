package com.warehouse.warehouse_platform.tenant.counting;

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
@Table(name = "count_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountLine {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "expected_qty", nullable = false, precision = 15, scale = 4)
    private BigDecimal expectedQty;

    @Column(name = "counted_qty", precision = 15, scale = 4)
    private BigDecimal countedQty;

    @Column(name = "variance", precision = 15, scale = 4, insertable = false, updatable = false)
    private BigDecimal variance;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (expectedQty == null) {
            expectedQty = BigDecimal.ZERO;
        }
    }
}