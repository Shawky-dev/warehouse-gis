package com.warehouse.warehouse_platform.tenant.role;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantRoleExceptionHandlerTest {

    @Test
    void handleRoleManagementException_shouldReturnStructuredResponse() {
        TenantRoleExceptionHandler handler = new TenantRoleExceptionHandler();

        var response = handler.handleRoleManagementException(
                TenantRoleManagementException.badRequest("Unknown permission code"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("BAD_REQUEST", response.getBody().code());
        assertEquals("Unknown permission code", response.getBody().message());
    }

    @Test
    void handleRoleManagementException_shouldSupportConflictResponse() {
        TenantRoleExceptionHandler handler = new TenantRoleExceptionHandler();

        var response = handler.handleRoleManagementException(
                TenantRoleManagementException.conflict("Role already exists"));

        assertEquals(409, response.getStatusCode().value());
        assertEquals("CONFLICT", response.getBody().code());
        assertEquals("Role already exists", response.getBody().message());
    }
}
