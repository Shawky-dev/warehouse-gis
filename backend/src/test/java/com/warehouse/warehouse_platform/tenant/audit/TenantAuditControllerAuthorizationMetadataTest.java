package com.warehouse.warehouse_platform.tenant.audit;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TenantAuditControllerAuthorizationMetadataTest {

    @Test
    void listAuditLogs_shouldRequireAuditViewPermission() throws Exception {
        Method method = TenantAuditController.class.getMethod(
                "listAuditLogs",
                String.class,
                int.class,
                int.class,
                String.class,
                String.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                Authentication.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(
                "hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).AUDIT_VIEW)",
                preAuthorize.value());
    }
}
