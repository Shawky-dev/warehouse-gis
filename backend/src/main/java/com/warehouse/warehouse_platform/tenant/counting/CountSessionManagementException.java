package com.warehouse.warehouse_platform.tenant.counting;

import org.springframework.http.HttpStatus;

public class CountSessionManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private CountSessionManagementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static CountSessionManagementException badRequest(String message) {
        return new CountSessionManagementException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static CountSessionManagementException notFound(String message) {
        return new CountSessionManagementException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static CountSessionManagementException conflict(String message) {
        return new CountSessionManagementException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}