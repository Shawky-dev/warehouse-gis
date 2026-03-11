package com.warehouse.warehouse_platform.tenant.warehouse.layout;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WarehouseLayoutControllerAuthorizationMetadataTest {

    @Test
    void listLayouts_shouldRequireWarehouseViewPermission() throws Exception {
        Method method = WarehouseLayoutController.class.getMethod(
                "listLayouts",
                String.class,
                int.class,
                int.class,
                String.class,
                Boolean.class,
                Authentication.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)",
                preAuthorize.value());
    }

    @Test
    void getLayout_shouldRequireWarehouseViewPermission() throws Exception {
        Method method = WarehouseLayoutController.class.getMethod(
                "getLayout",
                String.class,
                UUID.class,
                Authentication.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)",
                preAuthorize.value());
    }

    @Test
    void createLayout_shouldRequireLayoutManagePermission() throws Exception {
        Method method = WarehouseLayoutController.class.getMethod(
                "createLayout",
                String.class,
                WarehouseLayoutController.CreateLayoutRequest.class,
                Authentication.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_LAYOUT_MANAGE)",
                preAuthorize.value());
    }

    @Test
    void createClassicPreset_shouldRequireLayoutManageAndConditionalActivatePermissions() throws Exception {
        Method method = WarehouseLayoutController.class.getMethod(
                "createClassicPreset",
                String.class,
                WarehouseLayoutController.CreateClassicPresetRequest.class,
                Authentication.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_LAYOUT_MANAGE) and (!#request.activate() or hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_LAYOUT_ACTIVATE))",
                preAuthorize.value());
    }

    @Test
    void updateLayout_shouldRequireLayoutManagePermission() throws Exception {
        Method method = WarehouseLayoutController.class.getMethod(
                "updateLayout",
                String.class,
                UUID.class,
                WarehouseLayoutController.UpdateLayoutRequest.class,
                Authentication.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_LAYOUT_MANAGE)",
                preAuthorize.value());
    }

    @Test
    void activateLayout_shouldRequireLayoutActivatePermission() throws Exception {
        Method method = WarehouseLayoutController.class.getMethod(
                "activateLayout",
                String.class,
                UUID.class,
                Authentication.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_LAYOUT_ACTIVATE)",
                preAuthorize.value());
    }

    @Test
    void deactivateLayout_shouldRequireLayoutActivatePermission() throws Exception {
        Method method = WarehouseLayoutController.class.getMethod(
                "deactivateLayout",
                String.class,
                UUID.class,
                Authentication.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_LAYOUT_ACTIVATE)",
                preAuthorize.value());
    }

    @Test
    void deleteLayout_shouldRequireHardDeletePermission() throws Exception {
        Method method = WarehouseLayoutController.class.getMethod(
                "deleteLayout",
                String.class,
                UUID.class,
                Authentication.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_HARD_DELETE)",
                preAuthorize.value());
    }
}