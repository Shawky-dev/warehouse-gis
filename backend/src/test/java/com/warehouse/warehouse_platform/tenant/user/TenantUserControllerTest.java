package com.warehouse.warehouse_platform.tenant.user;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantUserControllerTest {

    private TenantAccessPolicy tenantAccessPolicy;
    private TenantUserManagementService tenantUserManagementService;
    private TenantUserController controller;

    @BeforeEach
    void setUp() {
        tenantAccessPolicy = mock(TenantAccessPolicy.class);
        tenantUserManagementService = mock(TenantUserManagementService.class);
        controller = new TenantUserController(tenantAccessPolicy, tenantUserManagementService);
    }

    @Test
    void listUsers_shouldValidateTenantAccessAndReturnPageResult() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("admin@acme.local", "n/a", "ROLE_ADMIN");

        TenantUserManagementService.UserResult user = new TenantUserManagementService.UserResult(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "admin@acme.local",
                "ADMIN",
                true,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null);

        TenantUserManagementService.UserPageResult result = new TenantUserManagementService.UserPageResult(
                List.of(user),
                0,
                20,
                1,
                1);

        when(tenantUserManagementService.listUsers(0, 20, "admin", true)).thenReturn(result);

        var response = controller.listUsers("acme", 0, 20, "admin", true, authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().totalElements());
        verify(tenantAccessPolicy).assertTenantAccess(authentication, "acme");
    }

    @Test
    void createUser_shouldPassAdminFlagFromAuthorities() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("admin@acme.local", "n/a", "ROLE_ADMIN");
        when(tenantUserManagementService.createUser(any(String.class), any(String.class), any(String.class), eq(true)))
                .thenReturn(new TenantUserManagementService.UserResult(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "new@acme.local",
                        "MANAGER",
                        true,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        null));

        var response = controller.createUser(
                "acme",
                new TenantUserController.CreateUserRequest("new@acme.local", "password123", "MANAGER"),
                authentication);

        assertEquals(200, response.getStatusCode().value());
        verify(tenantAccessPolicy).assertTenantAccess(authentication, "acme");
        verify(tenantUserManagementService).createUser("new@acme.local", "password123", "MANAGER", true);
    }

    @Test
    void deactivateUser_shouldUseAuthenticatedPrincipalName() {
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("admin@acme.local", "n/a");

        var response = controller.deactivateUser("acme", userId, authentication);

        assertEquals(204, response.getStatusCode().value());
        verify(tenantAccessPolicy).assertTenantAccess(authentication, "acme");
        verify(tenantUserManagementService).deactivateUser(userId, "admin@acme.local", false);
    }

    @Test
    void reactivateUser_shouldDelegateToService() {
        UUID userId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("admin@acme.local", "n/a", "ROLE_ADMIN");

        var response = controller.reactivateUser("acme", userId, authentication);

        assertEquals(204, response.getStatusCode().value());
        verify(tenantAccessPolicy).assertTenantAccess(authentication, "acme");
        verify(tenantUserManagementService).reactivateUser(userId);
    }
}
