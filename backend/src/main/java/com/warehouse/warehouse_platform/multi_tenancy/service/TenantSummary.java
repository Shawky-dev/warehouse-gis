package com.warehouse.warehouse_platform.multi_tenancy.service;

public record TenantSummary(
        String tenantId,
        String schema) {
}
