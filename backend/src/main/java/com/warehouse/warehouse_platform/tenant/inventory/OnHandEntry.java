package com.warehouse.warehouse_platform.tenant.inventory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Spring Data projection for the on-hand aggregation query.
 * Maps the native SQL aliases: locationId, productId, qtyOnHand.
 */
public interface OnHandEntry {
    UUID getLocationId();
    UUID getProductId();
    BigDecimal getQtyOnHand();
}
