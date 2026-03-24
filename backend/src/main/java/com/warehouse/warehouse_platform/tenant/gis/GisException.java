package com.warehouse.warehouse_platform.tenant.gis;

import org.springframework.http.HttpStatus;

public class GisException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private GisException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static GisException badRequest(String message) {
        return new GisException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static GisException notFound(String message) {
        return new GisException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static GisException conflict(String message) {
        return new GisException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
