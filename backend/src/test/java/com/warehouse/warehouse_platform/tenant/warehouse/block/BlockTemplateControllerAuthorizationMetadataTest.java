package com.warehouse.warehouse_platform.tenant.warehouse.block;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BlockTemplateControllerAuthorizationMetadataTest {

    @Test
    void listTemplates_shouldRequireWarehouseViewPermission() throws Exception {
        Method method = BlockTemplateController.class.getMethod(
                "listTemplates",
                String.class,
                int.class,
                int.class,
                String.class,
                Authentication.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_VIEW)",
                preAuthorize.value());
    }

    @Test
    void getTemplate_shouldRequireWarehouseViewPermission() throws Exception {
        Method method = BlockTemplateController.class.getMethod(
                "getTemplate",
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
    void createTemplate_shouldRequireTemplateManagePermission() throws Exception {
        Method method = BlockTemplateController.class.getMethod(
                "createTemplate",
                String.class,
                BlockTemplateController.TemplateRequest.class,
                Authentication.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_TEMPLATE_MANAGE)",
                preAuthorize.value());
    }

    @Test
    void updateTemplate_shouldRequireTemplateManagePermission() throws Exception {
        Method method = BlockTemplateController.class.getMethod(
                "updateTemplate",
                String.class,
                UUID.class,
                BlockTemplateController.TemplateRequest.class,
                Authentication.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).WAREHOUSE_TEMPLATE_MANAGE)",
                preAuthorize.value());
    }

    @Test
    void deleteTemplate_shouldRequireHardDeletePermission() throws Exception {
        Method method = BlockTemplateController.class.getMethod(
                "deleteTemplate",
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