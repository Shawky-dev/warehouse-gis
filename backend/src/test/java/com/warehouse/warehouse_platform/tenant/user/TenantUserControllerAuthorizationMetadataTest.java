package com.warehouse.warehouse_platform.tenant.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TenantUserControllerAuthorizationMetadataTest {

    @Test
    void listUsers_shouldRequireUsersViewPermission() throws Exception {
        Method method = TenantUserController.class.getMethod(
                "listUsers",
                String.class,
                int.class,
                int.class,
                String.class,
                Boolean.class,
                Authentication.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).USERS_VIEW)",
                preAuthorize.value());
    }

    @Test
    void createUser_shouldRequireUsersCreatePermission() throws Exception {
        Method method = TenantUserController.class.getMethod(
                "createUser",
                String.class,
                TenantUserController.CreateUserRequest.class,
                Authentication.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).USERS_CREATE)",
                preAuthorize.value());
    }

    @Test
    void deactivateUser_shouldRequireUsersDeactivatePermission() throws Exception {
        Method method = TenantUserController.class.getMethod(
                "deactivateUser",
                String.class,
                java.util.UUID.class,
                Authentication.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).USERS_DEACTIVATE)",
                preAuthorize.value());
    }

    @Test
    void reactivateUser_shouldRequireUsersReactivatePermission() throws Exception {
        Method method = TenantUserController.class.getMethod(
                "reactivateUser",
                String.class,
                java.util.UUID.class,
                Authentication.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).USERS_REACTIVATE)",
                preAuthorize.value());
    }
}
