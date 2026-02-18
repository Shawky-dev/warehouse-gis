package com.warehouse.warehouse_platform.auth;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthExceptionHandlerTest {

    @Test
    void handleRefreshTokenException_shouldReturnUnauthorizedContract() {
        AuthExceptionHandler handler = new AuthExceptionHandler();

        var response = handler.handleRefreshTokenException(new RefreshTokenException("Refresh token expired"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("UNAUTHORIZED", response.getBody().code());
        assertEquals("Refresh token expired", response.getBody().message());
    }
}
