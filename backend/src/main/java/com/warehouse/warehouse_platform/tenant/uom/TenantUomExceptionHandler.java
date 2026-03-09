package com.warehouse.warehouse_platform.tenant.uom;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TenantUomController.class)
public class TenantUomExceptionHandler {

    @ExceptionHandler(TenantUomManagementException.class)
    public ResponseEntity<ErrorResponse> handleUomException(TenantUomManagementException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = "Invalid request";
        FieldError fieldError = exception.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.getDefaultMessage() != null && !fieldError.getDefaultMessage().isBlank()) {
            message = fieldError.getDefaultMessage();
        }

        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", exception.getMessage()));
    }

    public record ErrorResponse(String code, String message) {
    }
}
