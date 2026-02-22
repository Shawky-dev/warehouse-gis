package com.warehouse.warehouse_platform.landlord.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LandlordUserExceptionHandlerTest {

    @Test
    void handleUserManagementException_shouldReturnStructuredContract() {
        LandlordUserExceptionHandler handler = new LandlordUserExceptionHandler();

        var response = handler.handleUserManagementException(
                LandlordUserManagementException.conflict("Email already exists"));

        assertEquals(409, response.getStatusCode().value());
        assertEquals("CONFLICT", response.getBody().code());
        assertEquals("Email already exists", response.getBody().message());
    }
}
