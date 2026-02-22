package com.warehouse.warehouse_platform.landlord;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LandlordControllerAuthorizationMetadataTest {

    @Test
    void getTenants_shouldRequireTenantsViewPermission() throws Exception {
        Method method = LandlordController.class.getMethod("getTenants");
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).TENANTS_VIEW)",
                preAuthorize.value());
    }

    @Test
    void createTenant_shouldRequireTenantsCreatePermission() throws Exception {
        Method method = LandlordController.class.getMethod(
                "createTenant",
                LandlordController.CreateTenantRequest.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.LandlordPermissions).TENANTS_CREATE)",
                preAuthorize.value());
    }
}
