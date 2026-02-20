package com.warehouse.warehouse_platform.multi_tenancy.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Stores the current tenant ID in thread-local storage so any code running
 * on the same thread can access tenant context without parameter passing.
 */
@Slf4j
public final class TenantContext {

    private TenantContext() {}

    private static InheritableThreadLocal<String> currentTenant = new InheritableThreadLocal<>();

    public static void setTenantId(String tenantId) {
        log.debug("Setting tenantId to " + tenantId);
        currentTenant.set(tenantId);
    }

    public static String getTenantId() {
        return currentTenant.get();
    }

    public static void clear(){
        currentTenant.remove();
    }
}