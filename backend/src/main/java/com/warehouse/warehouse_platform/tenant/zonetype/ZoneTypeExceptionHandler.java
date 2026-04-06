package com.warehouse.warehouse_platform.tenant.zonetype;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ZoneTypeController.class)
public class ZoneTypeExceptionHandler {

    @ExceptionHandler(ZoneTypeException.class)
    public ResponseEntity<ErrorResponse> handleZoneTypeException(ZoneTypeException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        FieldError fe = ex.getBindingResult().getFieldError();
        String msg = (fe != null && fe.getDefaultMessage() != null) ? fe.getDefaultMessage() : "Invalid request";
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", msg));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    public record ErrorResponse(String code, String message) {
    }
}
