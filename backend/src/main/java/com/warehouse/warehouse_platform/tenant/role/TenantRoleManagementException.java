package com.warehouse.warehouse_platform.tenant.role;

import org.springframework.http.HttpStatus;

public class TenantRoleManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private TenantRoleManagementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static TenantRoleManagementException badRequest(String message) {
        return new TenantRoleManagementException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static TenantRoleManagementException notFound(String message) {
        return new TenantRoleManagementException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static TenantRoleManagementException conflict(String message) {
        return new TenantRoleManagementException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static TenantRoleManagementException forbidden(String message) {
        return new TenantRoleManagementException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
