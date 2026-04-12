package com.warehouse.warehouse_platform.tenant.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DashboardControllerAuthorizationMetadataTest {

    @Test
    void getSpatialKpis_shouldRequireAnySpatialPermission() throws Exception {
        Method method = DashboardController.class.getMethod(
                "getSpatialKpis",
                String.class,
                Authentication.class);

        assertPreAuthorizeValue(method, "hasAnyAuthority("
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_VIEW,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_VIEW,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HAZARD_BUFFERS_VIEW,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_DATA_LAYERS_VIEW"
                + ")");
    }

    @Test
    void getInventoryOps_shouldRequireAnyInventoryPermission() throws Exception {
        Method method = DashboardController.class.getMethod(
                "getInventoryOps",
                String.class,
                Authentication.class);

        assertPreAuthorizeValue(method, "hasAnyAuthority("
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_RECEIVE,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_TRANSFER,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_ADJUST,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).RECEIPTS_VIEW,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).DISPATCHES_VIEW"
                + ")");
    }

    @Test
    void getWarnings_shouldRequireAnyWarningPermission() throws Exception {
        Method method = DashboardController.class.getMethod(
                "getWarnings",
                String.class,
                Authentication.class);

        assertPreAuthorizeValue(method, "hasAnyAuthority("
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).INVENTORY_VIEW,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_VIEW,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HAZARD_BUFFERS_VIEW"
                + ")");
    }

    @Test
    void getMasterData_shouldRequireAnyMasterDataPermission() throws Exception {
        Method method = DashboardController.class.getMethod(
                "getMasterData",
                String.class,
                Authentication.class);

        assertPreAuthorizeValue(method, "hasAnyAuthority("
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).PRODUCTS_VIEW,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).CATEGORIES_VIEW,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).SUPPLIERS_VIEW,"
                + "T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).UOMS_VIEW"
                + ")");
    }

    @Test
    void getActivity_shouldRequireAuditViewPermission() throws Exception {
        Method method = DashboardController.class.getMethod(
                "getActivity",
                String.class,
                Authentication.class);

        assertPreAuthorizeValue(
                method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).AUDIT_VIEW)");
    }

    private void assertPreAuthorizeValue(Method method, String expected) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(expected, preAuthorize.value());
    }
}
