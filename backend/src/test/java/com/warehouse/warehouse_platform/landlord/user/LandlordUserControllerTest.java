package com.warehouse.warehouse_platform.landlord.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LandlordUserControllerTest {

    private LandlordUserManagementService landlordUserManagementService;
    private LandlordUserController controller;

    @BeforeEach
    void setUp() {
        landlordUserManagementService = mock(LandlordUserManagementService.class);
        controller = new LandlordUserController(landlordUserManagementService);
    }

    @Test
    void listUsers_shouldReturnPageResult() {
        LandlordUserManagementService.UserResult user = new LandlordUserManagementService.UserResult(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "admin@system.local",
                "ADMIN",
                true,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null);

        LandlordUserManagementService.UserPageResult result = new LandlordUserManagementService.UserPageResult(
                List.of(user),
                0,
                20,
                1,
                1);

        when(landlordUserManagementService.listUsers(0, 20, "admin", true)).thenReturn(result);

        var response = controller.listUsers(0, 20, "admin", true);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().totalElements());
        assertEquals("admin@system.local", response.getBody().content().getFirst().email());
    }

    @Test
    void deactivateUser_shouldUseAuthenticatedPrincipalName() {
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("admin@system.local", "n/a");

        var response = controller.deactivateUser(userId, authentication);

        assertEquals(204, response.getStatusCode().value());
        verify(landlordUserManagementService).deactivateUser(userId, "admin@system.local");
    }
}
