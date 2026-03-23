package com.warehouse.warehouse_platform.tenant.gis;

import org.springframework.http.HttpStatus;

public class GeoServerProvisioningException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private GeoServerProvisioningException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static GeoServerProvisioningException badRequest(String message) {
        return new GeoServerProvisioningException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static GeoServerProvisioningException notFound(String message) {
        return new GeoServerProvisioningException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static GeoServerProvisioningException serverError(String message) {
        return new GeoServerProvisioningException(HttpStatus.INTERNAL_SERVER_ERROR, "GEOSERVER_ERROR", message);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
