package com.warehouse.warehouse_platform.tenant.dispatch;

import org.springframework.http.HttpStatus;

public class DispatchManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private DispatchManagementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static DispatchManagementException badRequest(String message) {
        return new DispatchManagementException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static DispatchManagementException notFound(String message) {
        return new DispatchManagementException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static DispatchManagementException conflict(String message) {
        return new DispatchManagementException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
