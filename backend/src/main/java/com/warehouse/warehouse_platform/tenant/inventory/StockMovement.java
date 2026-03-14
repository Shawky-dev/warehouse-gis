package com.warehouse.warehouse_platform.tenant.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Immutable ledger row. Every stock change — receive, transfer, adjust, pick —
 * is recorded here. Stock quantities are always derived from these rows.
 * <p>
 * qty is the signed delta relative to the location:
 * positive = stock entering the location, negative = stock leaving.
 * <p>
 * For transfers, two rows are written atomically:
 * one TRANSFER_OUT on the source and one TRANSFER_IN on the destination,
 * both sharing the same reference_id.
 */
@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    private UUID id;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal qty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    /** Links the TRANSFER_OUT and TRANSFER_IN rows of a transfer pair. */
    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null)
            id = UUID.randomUUID();
        if (createdAt == null)
            createdAt = Instant.now();
    }
}
