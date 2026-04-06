package com.warehouse.warehouse_platform.tenant.hazardtype;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = HazardTypeController.class)
public class HazardTypeExceptionHandler {

    @ExceptionHandler(HazardTypeException.class)
    public ResponseEntity<ErrorResponse> handleHazardTypeException(HazardTypeException ex) {
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
