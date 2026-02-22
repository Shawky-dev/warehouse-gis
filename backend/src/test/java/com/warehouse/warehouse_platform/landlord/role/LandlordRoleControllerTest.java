package com.warehouse.warehouse_platform.landlord.role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LandlordRoleControllerTest {

    private LandlordRoleManagementService landlordRoleManagementService;
    private LandlordRoleController controller;

    @BeforeEach
    void setUp() {
        landlordRoleManagementService = mock(LandlordRoleManagementService.class);
        controller = new LandlordRoleController(landlordRoleManagementService);
    }

    @Test
    void createRole_shouldReturnCreatedRolePayload() {
        when(landlordRoleManagementService.createRole(
                "AUDITOR",
                "Auditor",
                "Read only",
                java.util.Set.of("landlord.users.view")))
                .thenReturn(new LandlordRoleManagementService.RoleDetails(
                        "AUDITOR",
                        "Auditor",
                        "Read only",
                        List.of("landlord.users.view")));

        var response = controller.createRole(
                new LandlordRoleController.CreateRoleRequest(
                        "AUDITOR",
                        "Auditor",
                        "Read only",
                        java.util.Set.of("landlord.users.view")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("AUDITOR", response.getBody().code());
        assertEquals("Auditor", response.getBody().name());
    }

    @Test
    void listPermissions_shouldReturnPermissionCatalog() {
        when(landlordRoleManagementService.listPermissions()).thenReturn(List.of(
                new LandlordRoleManagementService.PermissionOption("landlord.users.view", "View users"),
                new LandlordRoleManagementService.PermissionOption("landlord.users.create", "Create users")
        ));

        var response = controller.listPermissions();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        assertEquals("landlord.users.view", response.getBody().getFirst().code());
    }

    @Test
    void updateRole_shouldReturnUpdatedRolePayload() {
        when(landlordRoleManagementService.updateRole(
                "MANAGER",
                "Operations Manager",
                "Ops scope",
                java.util.Set.of("landlord.users.view")))
                .thenReturn(new LandlordRoleManagementService.RoleDetails(
                        "MANAGER",
                        "Operations Manager",
                        "Ops scope",
                        List.of("landlord.users.view")));

        var response = controller.updateRole(
                "MANAGER",
                new LandlordRoleController.UpdateRoleRequest(
                        "Operations Manager",
                        "Ops scope",
                        java.util.Set.of("landlord.users.view")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("MANAGER", response.getBody().code());
        assertEquals("Operations Manager", response.getBody().name());
    }
}
