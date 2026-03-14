package com.warehouse.warehouse_platform.tenant.inventory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Spring Data projection for stock aggregation queries.
 * Maps the native SQL aliases: locationId, productId, lotNumber, qtyStock.
 */
public interface StockEntry {
    UUID getLocationId();

    UUID getProductId();

    String getLotNumber();

    BigDecimal getQtyStock();
}
