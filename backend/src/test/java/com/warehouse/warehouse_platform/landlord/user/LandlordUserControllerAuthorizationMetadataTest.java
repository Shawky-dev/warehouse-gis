package com.warehouse.warehouse_platform.landlord.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

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
                LandlordUserController.CreateUserRequest.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).USERS_CREATE)",
                preAuthorize.value());
    }
}
