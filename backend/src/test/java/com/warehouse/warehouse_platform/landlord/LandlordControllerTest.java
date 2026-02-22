package com.warehouse.warehouse_platform.landlord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.warehouse.warehouse_platform.multi_tenancy.service.TenantManagementService;
import com.warehouse.warehouse_platform.multi_tenancy.service.TenantSummary;

class LandlordControllerTest {

    private TenantManagementService tenantManagementService;
    private LandlordController controller;

    @BeforeEach
    void setUp() {
        tenantManagementService = mock(TenantManagementService.class);
        LandlordAccessService landlordAccessService = mock(LandlordAccessService.class);
        controller = new LandlordController(landlordAccessService, tenantManagementService);
    }

    @Test
    void getTenants_shouldReturnSummaryList() {
        when(tenantManagementService.getTenants()).thenReturn(List.of(
                new TenantSummary("acme", "acme"),
                new TenantSummary("beta", "beta")));

        var response = controller.getTenants();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        assertEquals("acme", response.getBody().getFirst().tenantId());
        assertEquals("acme", response.getBody().getFirst().schema());
    }

    @Test
    void createTenant_shouldDelegateToService() {
        controller.createTenant(new LandlordController.CreateTenantRequest(
                "acme",
                "acme",
                new LandlordController.TenantAdminRequest("admin@acme.local", "admin123")));

        verify(tenantManagementService).createTenant("acme", "acme", "admin@acme.local", "admin123");
    }
}
