package com.warehouse.warehouse_platform.tenant.scan;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class ScanResolveResult {

    private final ScanType type;

    // PRODUCT / LOT
    private final UUID productId;
    private final String productSku;
    private final String productName;
    private final Boolean trackLot;
    private final Boolean trackExpiry;

    // LOCATION
    private final UUID locationId;
    private final String locationPathLabel;
    private final String locationKindName;
    private final String scanCode;
    private final String fullCode;

    // LOT
    private final String lotNumber;

    // Documents
    private final UUID receiptId;
    private final UUID dispatchId;
    private final UUID countSessionId;

    // RECEIPT_LINE (Stock Unit)
    private final UUID receiptLineId;
    private final BigDecimal lineQty;

    /** Human-readable label shown in the UI after a successful resolve. */
    private final String displayLabel;
}
