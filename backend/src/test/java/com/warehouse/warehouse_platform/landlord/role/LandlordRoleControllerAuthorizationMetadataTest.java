package com.warehouse.warehouse_platform.landlord.role;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LandlordRoleControllerAuthorizationMetadataTest {

    @Test
    void listRoles_shouldRequireRolesEditPermission() throws Exception {
        Method method = LandlordRoleController.class.getMethod("listRoles");
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).ROLES_EDIT)",
                preAuthorize.value());
    }

    @Test
    void updateRole_shouldBeMappedToPutRolePath() throws Exception {
        Method method = LandlordRoleController.class.getMethod(
                "updateRole",
                String.class,
                LandlordRoleController.UpdateRoleRequest.class,
                Authentication.class);

        org.springframework.web.bind.annotation.PutMapping mapping = method.getAnnotation(
                org.springframework.web.bind.annotation.PutMapping.class);

        assertNotNull(mapping);
        assertEquals("/roles/{roleCode}", mapping.value()[0]);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).ROLES_EDIT)",
                preAuthorize.value());
    }

    @Test
    void createRole_shouldBeMappedToPostRolesPathAndRequireAdminRole() throws Exception {
        Method method = LandlordRoleController.class.getMethod(
                "createRole",
                LandlordRoleController.CreateRoleRequest.class);

        org.springframework.web.bind.annotation.PostMapping mapping = method.getAnnotation(
                org.springframework.web.bind.annotation.PostMapping.class);

        assertNotNull(mapping);
        assertEquals("/roles", mapping.value()[0]);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals("hasRole('ADMIN')", preAuthorize.value());
    }
}
