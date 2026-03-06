package com.warehouse.warehouse_platform.tenant.access;

import org.springframework.security.core.Authentication;

public interface TenantAccessPolicy {

    void assertTenantExists(String tenantSlug);

    void assertTenantAccess(Authentication authentication, String tenantSlug);
}
