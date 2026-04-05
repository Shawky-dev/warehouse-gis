package com.warehouse.warehouse_platform.tenant.receipt;

import com.warehouse.warehouse_platform.tenant.gis.GisZoneViolationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

@RestControllerAdvice(assignableTypes = ReceiptController.class)
@SuppressWarnings("null")
public class ReceiptExceptionHandler {

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

    public record ZoneViolationResponse(
            String error,
            String message,
            String violationAction,
            GisZoneViolationException.ZoneSummary violatedZone,
            java.util.List<GisZoneViolationException.ZoneSummary> suggestedZones) {
    }
}
