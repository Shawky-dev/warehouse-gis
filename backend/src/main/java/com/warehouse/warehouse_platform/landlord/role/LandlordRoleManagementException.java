package com.warehouse.warehouse_platform.landlord.role;

import org.springframework.http.HttpStatus;

public class LandlordRoleManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private LandlordRoleManagementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static LandlordRoleManagementException badRequest(String message) {
        return new LandlordRoleManagementException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static LandlordRoleManagementException notFound(String message) {
        return new LandlordRoleManagementException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static LandlordRoleManagementException conflict(String message) {
        return new LandlordRoleManagementException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static LandlordRoleManagementException forbidden(String message) {
        return new LandlordRoleManagementException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
