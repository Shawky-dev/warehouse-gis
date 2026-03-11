package com.warehouse.warehouse_platform.tenant.warehouse.block;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LayoutBlockControllerAuthorizationMetadataTest {

    @Test
    void getTree_shouldRequireWarehouseViewPermission() throws Exception {
        Method method = LayoutBlockController.class.getMethod(
                "getTree",
                String.class,
                UUID.class,
                Authentication.class);

        assertPermission(method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)");
    }

    @Test
    void getBlock_shouldRequireWarehouseViewPermission() throws Exception {
        Method method = LayoutBlockController.class.getMethod(
                "getBlock",
                String.class,
                UUID.class,
                UUID.class,
                Authentication.class);

        assertPermission(method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)");
    }

    @Test
    void addBlock_shouldRequireBlockEditPermission() throws Exception {
        Method method = LayoutBlockController.class.getMethod(
                "addBlock",
                String.class,
                UUID.class,
                LayoutBlockController.AddBlockRequest.class,
                Authentication.class);

        assertPermission(method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_BLOCK_EDIT)");
    }

    @Test
    void addBlocks_shouldRequireBlockEditPermission() throws Exception {
        Method method = LayoutBlockController.class.getMethod(
                "addBlocks",
                String.class,
                UUID.class,
                LayoutBlockController.AddBlocksRequest.class,
                Authentication.class);

        assertPermission(method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_BLOCK_EDIT)");
    }

    @Test
    void copySubtree_shouldRequireBlockEditPermission() throws Exception {
        Method method = LayoutBlockController.class.getMethod(
                "copySubtree",
                String.class,
                UUID.class,
                LayoutBlockController.CopySubtreeRequest.class,
                Authentication.class);

        assertPermission(method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_BLOCK_EDIT)");
    }

    @Test
    void moveBlock_shouldRequireBlockEditPermission() throws Exception {
        Method method = LayoutBlockController.class.getMethod(
                "moveBlock",
                String.class,
                UUID.class,
                UUID.class,
                LayoutBlockController.MoveBlockRequest.class,
                Authentication.class);

        assertPermission(method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_BLOCK_EDIT)");
    }

    @Test
    void reassignTemplate_shouldRequireBlockEditPermission() throws Exception {
        Method method = LayoutBlockController.class.getMethod(
                "reassignTemplate",
                String.class,
                UUID.class,
                UUID.class,
                LayoutBlockController.ReassignTemplateRequest.class,
                Authentication.class);

        assertPermission(method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_BLOCK_EDIT)");
    }

    @Test
    void updateMetadata_shouldRequireBlockEditPermission() throws Exception {
        Method method = LayoutBlockController.class.getMethod(
                "updateMetadata",
                String.class,
                UUID.class,
                UUID.class,
                LayoutBlockController.UpdateBlockMetadataRequest.class,
                Authentication.class);

        assertPermission(method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_BLOCK_EDIT)");
    }

    @Test
    void removeBlock_shouldRequireHardDeletePermission() throws Exception {
        Method method = LayoutBlockController.class.getMethod(
                "removeBlock",
                String.class,
                UUID.class,
                UUID.class,
                Authentication.class);

        assertPermission(method,
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_HARD_DELETE)");
    }

    private void assertPermission(Method method, String expectedPermission) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(expectedPermission, preAuthorize.value());
    }
}