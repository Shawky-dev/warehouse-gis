package com.warehouse.warehouse_platform.tenant.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantUserExceptionHandlerTest {

    @Test
    void handleUserManagementException_shouldReturnStructuredContract() {
        TenantUserExceptionHandler handler = new TenantUserExceptionHandler();

        var response = handler.handleUserManagementException(
                TenantUserManagementException.conflict("Email already exists"));

        assertEquals(409, response.getStatusCode().value());
        assertEquals("CONFLICT", response.getBody().code());
        assertEquals("Email already exists", response.getBody().message());
    }
}
