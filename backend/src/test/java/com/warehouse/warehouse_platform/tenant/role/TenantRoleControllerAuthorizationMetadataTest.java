package com.warehouse.warehouse_platform.tenant.role;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TenantRoleControllerAuthorizationMetadataTest {

    @Test
    void listRoles_shouldRequireRolesEditPermission() throws Exception {
        Method method = TenantRoleController.class.getMethod("listRoles", String.class, Authentication.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).ROLES_EDIT)",
                preAuthorize.value());
    }

    @Test
    void updateRole_shouldBeMappedToPutRolePath() throws Exception {
        Method method = TenantRoleController.class.getMethod(
                "updateRole",
                String.class,
                String.class,
                TenantRoleController.UpdateRoleRequest.class,
                Authentication.class);

        org.springframework.web.bind.annotation.PutMapping mapping = method.getAnnotation(
                org.springframework.web.bind.annotation.PutMapping.class);

        assertNotNull(mapping);
        assertEquals("/roles/{roleCode}", mapping.value()[0]);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).ROLES_EDIT)",
                preAuthorize.value());
    }

    @Test
    void createRole_shouldBeMappedToPostRolesPathAndRequireAdminRole() throws Exception {
        Method method = TenantRoleController.class.getMethod(
                "createRole",
                String.class,
                TenantRoleController.CreateRoleRequest.class,
                Authentication.class);

        org.springframework.web.bind.annotation.PostMapping mapping = method.getAnnotation(
                org.springframework.web.bind.annotation.PostMapping.class);

        assertNotNull(mapping);
        assertEquals("/roles", mapping.value()[0]);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals("hasRole('ADMIN')", preAuthorize.value());
    }
}
