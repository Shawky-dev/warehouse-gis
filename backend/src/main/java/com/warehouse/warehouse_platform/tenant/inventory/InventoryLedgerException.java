package com.warehouse.warehouse_platform.tenant.inventory;

import org.springframework.http.HttpStatus;

public class InventoryLedgerException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private InventoryLedgerException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static InventoryLedgerException badRequest(String message) {
        return new InventoryLedgerException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static InventoryLedgerException notFound(String message) {
        return new InventoryLedgerException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
