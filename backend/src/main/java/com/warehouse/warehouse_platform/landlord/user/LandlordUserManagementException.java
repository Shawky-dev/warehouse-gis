package com.warehouse.warehouse_platform.landlord.user;

import org.springframework.http.HttpStatus;

public class LandlordUserManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private LandlordUserManagementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static LandlordUserManagementException badRequest(String message) {
        return new LandlordUserManagementException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static LandlordUserManagementException notFound(String message) {
        return new LandlordUserManagementException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static LandlordUserManagementException conflict(String message) {
        return new LandlordUserManagementException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static LandlordUserManagementException forbidden(String message) {
        return new LandlordUserManagementException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
