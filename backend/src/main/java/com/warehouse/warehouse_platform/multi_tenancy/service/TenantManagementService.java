package com.warehouse.warehouse_platform.multi_tenancy.service;

public interface TenantManagementService {
    void createTenant(String tenantId, String schema);
}
