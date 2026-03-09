package com.warehouse.warehouse_platform.tenant.uom;

import org.springframework.http.HttpStatus;

public class TenantUomManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private TenantUomManagementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static TenantUomManagementException badRequest(String message) {
        return new TenantUomManagementException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static TenantUomManagementException notFound(String message) {
        return new TenantUomManagementException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static TenantUomManagementException conflict(String message) {
        return new TenantUomManagementException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static TenantUomManagementException forbidden(String message) {
        return new TenantUomManagementException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
