package com.warehouse.warehouse_platform.rbac.role;

import org.springframework.http.HttpStatus;

public class RoleManagementCoreException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private RoleManagementCoreException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static RoleManagementCoreException badRequest(String message) {
        return new RoleManagementCoreException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static RoleManagementCoreException notFound(String message) {
        return new RoleManagementCoreException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static RoleManagementCoreException conflict(String message) {
        return new RoleManagementCoreException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static RoleManagementCoreException forbidden(String message) {
        return new RoleManagementCoreException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
