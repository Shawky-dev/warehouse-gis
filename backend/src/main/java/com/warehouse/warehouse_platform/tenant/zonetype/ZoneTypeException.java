package com.warehouse.warehouse_platform.tenant.zonetype;

import org.springframework.http.HttpStatus;

public class ZoneTypeException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private ZoneTypeException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ZoneTypeException badRequest(String message) {
        return new ZoneTypeException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ZoneTypeException notFound(String message) {
        return new ZoneTypeException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ZoneTypeException conflict(String message) {
        return new ZoneTypeException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static ZoneTypeException forbidden(String message) {
        return new ZoneTypeException(HttpStatus.UNPROCESSABLE_ENTITY, "FORBIDDEN_OPERATION", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
