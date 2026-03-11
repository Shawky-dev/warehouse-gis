package com.warehouse.warehouse_platform.tenant.warehouse.common;

import jakarta.persistence.RollbackException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;
import java.util.Objects;

@RestControllerAdvice(basePackages = "com.warehouse.warehouse_platform.tenant.warehouse")
public class WarehouseExceptionHandler {

    private static final String POSITION_CONFLICT_MESSAGE = "Another block already uses this position under the selected parent.";
    private static final String DATA_CONFLICT_MESSAGE = "The warehouse request conflicts with existing data.";

    @ExceptionHandler(WarehouseManagementException.class)
    public ResponseEntity<ErrorResponse> handleWarehouseException(WarehouseManagementException exception) {
        return ResponseEntity.status(Objects.requireNonNull(exception.getStatus(), "status must not be null"))
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = "Invalid request";
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String defaultMessage = fieldError == null ? null : fieldError.getDefaultMessage();
        if (defaultMessage != null && !defaultMessage.isBlank()) {
            message = defaultMessage;
        }

        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            TransactionSystemException.class,
            RollbackException.class
    })
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(Exception exception) {
        String message = isLayoutBlockPositionConflict(exception)
                ? POSITION_CONFLICT_MESSAGE
                : DATA_CONFLICT_MESSAGE;
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", message));
    }

    private boolean isLayoutBlockPositionConflict(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("uq_layout_blocks_position_rooted")
                        || normalized.contains("uq_layout_blocks_position_root")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public record ErrorResponse(String code, String message) {
    }
}
