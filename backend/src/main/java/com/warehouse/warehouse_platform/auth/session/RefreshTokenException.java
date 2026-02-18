package com.warehouse.warehouse_platform.auth.session;

public class RefreshTokenException extends RuntimeException {
    public RefreshTokenException(String message) {
        super(message);
    }
}
