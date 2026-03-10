package com.warehouse.warehouse_platform.tenant.category;

import org.springframework.http.HttpStatus;

public class TenantCategoryManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private TenantCategoryManagementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static TenantCategoryManagementException badRequest(String message) {
        return new TenantCategoryManagementException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static TenantCategoryManagementException notFound(String message) {
        return new TenantCategoryManagementException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static TenantCategoryManagementException conflict(String message) {
        return new TenantCategoryManagementException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static TenantCategoryManagementException forbidden(String message) {
        return new TenantCategoryManagementException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
