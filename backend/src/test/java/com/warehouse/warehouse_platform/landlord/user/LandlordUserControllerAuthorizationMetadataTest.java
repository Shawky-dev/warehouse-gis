package com.warehouse.warehouse_platform.landlord.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LandlordUserControllerAuthorizationMetadataTest {

    @Test
    void listUsers_shouldRequireUsersViewPermission() throws Exception {
        Method method = LandlordUserController.class.getMethod("listUsers", int.class, int.class, String.class, Boolean.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).USERS_VIEW)",
                preAuthorize.value());
    }

    @Test
    void createUser_shouldRequireUsersCreatePermission() throws Exception {
        Method method = LandlordUserController.class.getMethod(
                "createUser",
                LandlordUserController.CreateUserRequest.class,
                Authentication.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).USERS_CREATE)",
                preAuthorize.value());
    }

    @Test
    void deactivateUser_shouldRequireUsersDeactivatePermission() throws Exception {
        Method method = LandlordUserController.class.getMethod(
                "deactivateUser",
                java.util.UUID.class,
                Authentication.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).USERS_DEACTIVATE)",
                preAuthorize.value());
    }

    @Test
    void reactivateUser_shouldRequireUsersReactivatePermission() throws Exception {
        Method method = LandlordUserController.class.getMethod("reactivateUser", java.util.UUID.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).USERS_REACTIVATE)",
                preAuthorize.value());
    }
}
