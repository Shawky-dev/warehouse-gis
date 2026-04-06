package com.warehouse.warehouse_platform.tenant.hazardtype;

import org.springframework.http.HttpStatus;

public class HazardTypeException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private HazardTypeException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static HazardTypeException badRequest(String message) {
        return new HazardTypeException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static HazardTypeException notFound(String message) {
        return new HazardTypeException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static HazardTypeException conflict(String message) {
        return new HazardTypeException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static HazardTypeException forbidden(String message) {
        return new HazardTypeException(HttpStatus.UNPROCESSABLE_ENTITY, "FORBIDDEN_OPERATION", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
