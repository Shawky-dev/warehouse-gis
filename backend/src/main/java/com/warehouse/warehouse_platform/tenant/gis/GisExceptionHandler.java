package com.warehouse.warehouse_platform.tenant.gis;

import com.warehouse.warehouse_platform.tenant.gis.controller.GisAdminController;
import com.warehouse.warehouse_platform.tenant.gis.controller.GisLayerController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.Objects;

@RestControllerAdvice(assignableTypes = {GisAdminController.class, GisLayerController.class})
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

    @ExceptionHandler(GisZoneViolationException.class)
    public ResponseEntity<ZoneViolationResponse> handleGisZoneViolationException(
            GisZoneViolationException exception) {
        return ResponseEntity.status(Objects.requireNonNull(exception.getStatus(), "status must not be null"))
                .body(new ZoneViolationResponse(
                        exception.getCode(),
                        exception.getMessage(),
                        exception.getViolatedZone(),
                        exception.getSuggestedZones()));
    }

    public record ErrorResponse(String code, String message) {
    }

    public record ZoneViolationResponse(
            String error,
            String message,
            GisZoneViolationException.ZoneSummary violatedZone,
            java.util.List<GisZoneViolationException.ZoneSummary> suggestedZones) {
    }
}
