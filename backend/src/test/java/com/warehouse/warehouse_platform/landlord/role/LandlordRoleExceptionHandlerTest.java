package com.warehouse.warehouse_platform.landlord.role;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LandlordRoleExceptionHandlerTest {

    @Test
    void handleRoleManagementException_shouldReturnStructuredResponse() {
        LandlordRoleExceptionHandler handler = new LandlordRoleExceptionHandler();

        var response = handler.handleRoleManagementException(
                LandlordRoleManagementException.badRequest("Unknown permission code"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("BAD_REQUEST", response.getBody().code());
        assertEquals("Unknown permission code", response.getBody().message());
    }

    @Test
    void handleRoleManagementException_shouldSupportConflictResponse() {
        LandlordRoleExceptionHandler handler = new LandlordRoleExceptionHandler();

        var response = handler.handleRoleManagementException(
                LandlordRoleManagementException.conflict("Role already exists"));

        assertEquals(409, response.getStatusCode().value());
        assertEquals("CONFLICT", response.getBody().code());
        assertEquals("Role already exists", response.getBody().message());
    }
}
