package com.warehouse.warehouse_platform.tenant.role;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantRoleControllerTest {

    private TenantAccessPolicy tenantAccessPolicy;
    private TenantRoleManagementService tenantRoleManagementService;
    private TenantRoleController controller;

    @BeforeEach
    void setUp() {
        tenantAccessPolicy = mock(TenantAccessPolicy.class);
        tenantRoleManagementService = mock(TenantRoleManagementService.class);
        controller = new TenantRoleController(tenantAccessPolicy, tenantRoleManagementService);
    }

    @Test
    void createRole_shouldReturnCreatedRolePayload() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("admin@acme.local", "n/a", "ROLE_ADMIN");

        when(tenantRoleManagementService.createRole(
                "AUDITOR",
                "Auditor",
                "Read only",
                java.util.Set.of("tenant.users.view"),
                false))
                .thenReturn(new TenantRoleManagementService.RoleDetails(
                        "AUDITOR",
                        "Auditor",
                        "Read only",
                        List.of("tenant.users.view"),
                        false));

        var response = controller.createRole(
                "acme",
                new TenantRoleController.CreateRoleRequest(
                        "AUDITOR",
                        "Auditor",
                        "Read only",
                        java.util.Set.of("tenant.users.view"),
                        false),
                authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("AUDITOR", response.getBody().code());
        verify(tenantAccessPolicy).assertTenantAccess(authentication, "acme");
    }

    @Test
    void listPermissions_shouldReturnPermissionCatalog() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("admin@acme.local", "n/a", "ROLE_ADMIN");

        when(tenantRoleManagementService.listPermissions()).thenReturn(List.of(
                new TenantRoleManagementService.PermissionOption("tenant.users.view", "View users"),
                new TenantRoleManagementService.PermissionOption("tenant.users.create", "Create users")
        ));

        var response = controller.listPermissions("acme", authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        assertEquals("tenant.users.view", response.getBody().getFirst().code());
        verify(tenantAccessPolicy).assertTenantAccess(authentication, "acme");
    }

    @Test
    void updateRole_shouldReturnUpdatedRolePayload() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("admin@acme.local", "n/a", "ROLE_ADMIN");

        when(tenantRoleManagementService.updateRole(
                "MANAGER",
                "Operations Manager",
                "Ops scope",
                java.util.Set.of("tenant.users.view"),
                true,
                true))
                .thenReturn(new TenantRoleManagementService.RoleDetails(
                        "MANAGER",
                        "Operations Manager",
                        "Ops scope",
                        List.of("tenant.users.view"),
                        true));

        var response = controller.updateRole(
                "acme",
                "MANAGER",
                new TenantRoleController.UpdateRoleRequest(
                        "Operations Manager",
                        "Ops scope",
                        java.util.Set.of("tenant.users.view"),
                        true),
                authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("MANAGER", response.getBody().code());
        verify(tenantAccessPolicy).assertTenantAccess(authentication, "acme");
    }
}
