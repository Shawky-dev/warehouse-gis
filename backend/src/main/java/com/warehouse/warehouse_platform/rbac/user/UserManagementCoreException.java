package com.warehouse.warehouse_platform.rbac.user;

import org.springframework.http.HttpStatus;

public class UserManagementCoreException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private UserManagementCoreException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static UserManagementCoreException badRequest(String message) {
        return new UserManagementCoreException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static UserManagementCoreException notFound(String message) {
        return new UserManagementCoreException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static UserManagementCoreException conflict(String message) {
        return new UserManagementCoreException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static UserManagementCoreException forbidden(String message) {
        return new UserManagementCoreException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
