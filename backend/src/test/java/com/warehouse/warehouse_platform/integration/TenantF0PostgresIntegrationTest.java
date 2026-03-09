package com.warehouse.warehouse_platform.integration;

import com.warehouse.warehouse_platform.multi_tenancy.service.TenantManagementService;
import com.warehouse.warehouse_platform.multi_tenancy.util.TenantContext;
import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.product.TenantProductManagementException;
import com.warehouse.warehouse_platform.tenant.product.TenantProductManagementService;
import com.warehouse.warehouse_platform.tenant.supplier.TenantSupplierManagementService;
import com.warehouse.warehouse_platform.tenant.uom.TenantUomManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TenantF0PostgresIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("warehouse_test")
            .withUsername("warehouse")
            .withPassword("warehouse");

    @Autowired
    private TenantManagementService tenantManagementService;

    @Autowired
    private TenantUomManagementService tenantUomManagementService;

    @Autowired
    private TenantSupplierManagementService tenantSupplierManagementService;

    @Autowired
    private TenantProductManagementService tenantProductManagementService;

    @Autowired
    private TenantAuditService tenantAuditService;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.private-key", () -> "classpath:keys/jwt-private.pem");
        registry.add("security.jwt.public-key", () -> "classpath:keys/jwt-public.pem");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldIsolateTenantDataAcrossSchemas() {
        String tenantA = uniqueTenant("acme");
        String tenantB = uniqueTenant("beta");

        createTenant(tenantA);
        createTenant(tenantB);

        TenantContext.setTenantId(tenantA);
        tenantUomManagementService.createUom("EA", "Each", "ea");

        TenantContext.setTenantId(tenantB);
        tenantUomManagementService.createUom("EA", "Each", "ea");

        TenantContext.setTenantId(tenantA);
        long tenantACount = tenantUomManagementService.listUoms(0, 50, null, null).totalElements();

        TenantContext.setTenantId(tenantB);
        long tenantBCount = tenantUomManagementService.listUoms(0, 50, null, null).totalElements();

        assertEquals(1, tenantACount);
        assertEquals(1, tenantBCount);
    }

    @Test
    void shouldExecuteProductLifecycleAndPersistAuditTrail() {
        String tenant = uniqueTenant("f0");
        createTenant(tenant);

        TenantContext.setTenantId(tenant);

        TenantUomManagementService.UomResult uom = tenantUomManagementService.createUom("PCS", "Pieces", "pcs");
        TenantSupplierManagementService.SupplierResult supplier = tenantSupplierManagementService.createSupplier(
                "SUP-1",
                "Supplier One",
                "Contact",
                "contact@example.com",
                "+201000000000",
                null);

        TenantProductManagementService.ProductResult createdProduct = tenantProductManagementService.createProduct(
                "SKU-1",
                "Product One",
                "Initial",
                uom.id(),
                true,
                false,
                Set.of(supplier.id()),
                supplier.id());

        tenantProductManagementService.softDeleteProduct(createdProduct.id());

        assertThrows(
                TenantProductManagementException.class,
                () -> tenantProductManagementService.hardDeleteProduct(createdProduct.id()));

        tenantProductManagementService.updateProduct(
                createdProduct.id(),
                "SKU-1",
                "Product One",
                "Updated",
                uom.id(),
                true,
                false,
                Set.of(),
                null);

        tenantProductManagementService.hardDeleteProduct(createdProduct.id());

        TenantAuditService.AuditPageResult auditPage = tenantAuditService.listAuditLogs(
                0,
                100,
                null,
                null,
                "PRODUCT",
                createdProduct.id().toString(),
                null,
                null);

        var actions = auditPage.content().stream().map(TenantAuditService.AuditLogItem::action).toList();
        assertTrue(actions.contains("PRODUCT_CREATE"));
        assertTrue(actions.contains("PRODUCT_SOFT_DELETE"));
        assertTrue(actions.contains("PRODUCT_UPDATE"));
        assertTrue(actions.contains("PRODUCT_HARD_DELETE"));
        assertTrue(auditPage.content().stream().allMatch(item -> "system".equals(item.actorEmail())));
    }

    private void createTenant(String tenantSlug) {
        tenantManagementService.createTenant(
                tenantSlug,
                tenantSlug,
                tenantSlug + "@acme.local",
                "Admin12345");
    }

    private String uniqueTenant(String prefix) {
        return (prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8)).toLowerCase();
    }
}
