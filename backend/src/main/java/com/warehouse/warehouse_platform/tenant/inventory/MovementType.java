package com.warehouse.warehouse_platform.tenant.inventory;

public enum MovementType {
    /** Inbound stock arriving at a location. */
    RECEIVE,
    /** Stock arriving from another location (paired with TRANSFER_OUT). */
    TRANSFER_IN,
    /** Stock leaving to another location (paired with TRANSFER_IN). */
    TRANSFER_OUT,
    /** Manual correction — can be positive or negative. */
    ADJUST,
    /** Stock removed for outbound — always negative qty. */
    PICK
}
