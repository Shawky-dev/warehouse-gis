package com.warehouse.warehouse_platform.tenant.product;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TenantProductControllerAuthorizationMetadataTest {

    @Test
    void hardDeleteProduct_shouldRequireHardDeletePermission() throws Exception {
        Method method = TenantProductController.class.getMethod(
                "hardDeleteProduct",
                String.class,
                UUID.class,
                Authentication.class);

        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
        assertNotNull(mapping);
        assertEquals("/{productId}", mapping.value()[0]);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).PRODUCTS_HARD_DELETE)",
                preAuthorize.value());
    }
}
