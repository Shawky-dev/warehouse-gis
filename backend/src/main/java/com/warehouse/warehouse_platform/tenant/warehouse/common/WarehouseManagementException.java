package com.warehouse.warehouse_platform.tenant.warehouse.common;

import org.springframework.http.HttpStatus;

public class WarehouseManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private WarehouseManagementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static WarehouseManagementException badRequest(String message) {
        return new WarehouseManagementException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static WarehouseManagementException notFound(String message) {
        return new WarehouseManagementException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static WarehouseManagementException conflict(String message) {
        return new WarehouseManagementException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static WarehouseManagementException forbidden(String message) {
        return new WarehouseManagementException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
