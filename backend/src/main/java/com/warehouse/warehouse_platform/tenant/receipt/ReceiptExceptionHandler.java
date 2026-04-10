package com.warehouse.warehouse_platform.tenant.receipt;

import com.warehouse.warehouse_platform.tenant.gis.StorageRuleViolationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Objects;

@RestControllerAdvice(assignableTypes = ReceiptController.class)
@SuppressWarnings("null")
public class ReceiptExceptionHandler {

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

    @ExceptionHandler(ReceiptManagementException.class)
    public ResponseEntity<ErrorResponse> handleReceiptException(ReceiptManagementException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = "Invalid request";
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String fieldMessage = fieldError != null ? fieldError.getDefaultMessage() : null;
        if (fieldMessage != null && !fieldMessage.isBlank()) {
            message = fieldMessage;
        }
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", exception.getMessage()));
    }

    public record ErrorResponse(String code, String message) {
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
