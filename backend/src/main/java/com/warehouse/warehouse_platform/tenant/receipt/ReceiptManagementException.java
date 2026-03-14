package com.warehouse.warehouse_platform.tenant.receipt;

import org.springframework.http.HttpStatus;

public class ReceiptManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private ReceiptManagementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ReceiptManagementException badRequest(String message) {
        return new ReceiptManagementException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ReceiptManagementException notFound(String message) {
        return new ReceiptManagementException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ReceiptManagementException conflict(String message) {
        return new ReceiptManagementException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
