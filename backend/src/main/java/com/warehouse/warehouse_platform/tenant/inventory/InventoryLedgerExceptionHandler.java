package com.warehouse.warehouse_platform.tenant.inventory;

import com.warehouse.warehouse_platform.tenant.gis.GisZoneViolationException;
import com.warehouse.warehouse_platform.tenant.gis.StorageRuleViolationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Objects;

@RestControllerAdvice(basePackages = "com.warehouse.warehouse_platform.tenant.inventory")
public class InventoryLedgerExceptionHandler {

    @ExceptionHandler(InventoryLedgerException.class)
    public ResponseEntity<ErrorResponse> handleInventoryException(InventoryLedgerException exception) {
        return ResponseEntity.status(Objects.requireNonNull(exception.getStatus()))
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(GisZoneViolationException.class)
    public ResponseEntity<ZoneViolationResponse> handleZoneViolation(GisZoneViolationException exception) {
        return ResponseEntity.status(Objects.requireNonNull(exception.getStatus()))
                .body(new ZoneViolationResponse(
                        exception.getCode(),
                        exception.getMessage(),
                        exception.getViolationAction(),
                        exception.getViolatedZone(),
                        exception.getSuggestedZones()));
    }

    @ExceptionHandler(StorageRuleViolationException.class)
    public ResponseEntity<StorageRuleViolationResponse> handleStorageRuleViolation(
            StorageRuleViolationException exception) {
        return ResponseEntity.status(Objects.requireNonNull(exception.getStatus()))
                .body(new StorageRuleViolationResponse(
                        exception.getCode(),
                        exception.getMessage(),
                        exception.getRuleType().name(),
                        exception.getViolationAction(),
                        exception.getViolatedZone(),
                        exception.getViolatedBuffer(),
                        exception.getRestrictedHazardTypes(),
                        exception.getRequiredZoneType(),
                        exception.getSuggestedZones()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError != null && fieldError.getDefaultMessage() != null
                ? fieldError.getDefaultMessage()
                : "Invalid request";
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", exception.getMessage()));
    }

    public record ErrorResponse(String code, String message) {
    }

    public record ZoneViolationResponse(
            String error,
            String message,
            String violationAction,
            GisZoneViolationException.ZoneSummary violatedZone,
            List<GisZoneViolationException.ZoneSummary> suggestedZones) {
    }

    public record StorageRuleViolationResponse(
            String error,
            String message,
            String ruleType,
            String violationAction,
            StorageRuleViolationException.ZoneSummary violatedZone,
            StorageRuleViolationException.HazardBufferSummary violatedBuffer,
            List<StorageRuleViolationException.HazardTypeSummary> restrictedHazardTypes,
            StorageRuleViolationException.ZoneTypeSummary requiredZoneType,
            List<StorageRuleViolationException.ZoneSummary> suggestedZones) {
    }
}
