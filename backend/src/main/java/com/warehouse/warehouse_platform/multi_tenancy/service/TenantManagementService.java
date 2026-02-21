package com.warehouse.warehouse_platform.multi_tenancy.service;

import java.util.List;

public interface TenantManagementService {
    void createTenant(String tenantId, String schema);

    List<TenantSummary> getTenants();
}
