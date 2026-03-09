package com.warehouse.warehouse_platform.tenant.product;

import org.springframework.http.HttpStatus;

public class TenantProductManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private TenantProductManagementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static TenantProductManagementException badRequest(String message) {
        return new TenantProductManagementException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static TenantProductManagementException notFound(String message) {
        return new TenantProductManagementException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static TenantProductManagementException conflict(String message) {
        return new TenantProductManagementException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static TenantProductManagementException forbidden(String message) {
        return new TenantProductManagementException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
