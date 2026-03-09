package com.warehouse.warehouse_platform.tenant.supplier;

import org.springframework.http.HttpStatus;

public class TenantSupplierManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private TenantSupplierManagementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static TenantSupplierManagementException badRequest(String message) {
        return new TenantSupplierManagementException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static TenantSupplierManagementException notFound(String message) {
        return new TenantSupplierManagementException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static TenantSupplierManagementException conflict(String message) {
        return new TenantSupplierManagementException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static TenantSupplierManagementException forbidden(String message) {
        return new TenantSupplierManagementException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
