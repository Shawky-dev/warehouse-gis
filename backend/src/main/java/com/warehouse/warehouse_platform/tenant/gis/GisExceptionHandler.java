package com.warehouse.warehouse_platform.tenant.gis;

import com.warehouse.warehouse_platform.tenant.gis.controller.GisAdminController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

@RestControllerAdvice(assignableTypes = GisAdminController.class)
public class GisExceptionHandler {

    @ExceptionHandler(GisException.class)
    public ResponseEntity<ErrorResponse> handleGisException(GisException exception) {
        return ResponseEntity.status(Objects.requireNonNull(exception.getStatus(), "status must not be null"))
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(GeoServerProvisioningException.class)
    public ResponseEntity<ErrorResponse> handleGeoServerProvisioningException(
            GeoServerProvisioningException exception) {
        return ResponseEntity.status(Objects.requireNonNull(exception.getStatus(), "status must not be null"))
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    public record ErrorResponse(String code, String message) {
    }
}
