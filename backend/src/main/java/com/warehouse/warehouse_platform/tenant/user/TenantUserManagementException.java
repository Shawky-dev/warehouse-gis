package com.warehouse.warehouse_platform.tenant.user;

import org.springframework.http.HttpStatus;

public class TenantUserManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private TenantUserManagementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static TenantUserManagementException badRequest(String message) {
        return new TenantUserManagementException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static TenantUserManagementException notFound(String message) {
        return new TenantUserManagementException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static TenantUserManagementException conflict(String message) {
        return new TenantUserManagementException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static TenantUserManagementException forbidden(String message) {
        return new TenantUserManagementException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
