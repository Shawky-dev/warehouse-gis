package com.warehouse.warehouse_platform.tenant.inventory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantF2InventoryMigrationContractTest {

    @Test
    void tenantChangelog_shouldReferenceInventoryMigrations() throws Exception {
        String changelog = Files.readString(Path.of("src/main/resources/db/changelog/db.changelog-tenant.yaml"));

        assertTrue(changelog.contains("V17__create_f2_inventory_ledger.sql"));
        assertTrue(changelog.contains("V18__grant_inventory_permissions_to_admin.sql"));
    }

    @Test
    void inventoryLedgerMigration_shouldCreateLedgerViewAndPermissions() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/tenant/V17__create_f2_inventory_ledger.sql"));

        assertTrue(migration.contains("CREATE TABLE stock_movements"));
        assertTrue(migration.contains("CREATE OR REPLACE VIEW v_stock AS"));
        assertTrue(migration.contains("tenant.inventory.view"));
        assertTrue(migration.contains("tenant.inventory.receive"));
        assertTrue(migration.contains("tenant.inventory.transfer"));
        assertTrue(migration.contains("tenant.inventory.adjust"));
    }

    @Test
    void inventoryPermissionBackfillMigration_shouldGrantAdminDefaults() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/tenant/V18__grant_inventory_permissions_to_admin.sql"));

        assertTrue(migration.contains("SELECT 'ADMIN', permission.code"));
        assertTrue(migration.contains("permission.code LIKE 'tenant.inventory.%'"));
        assertTrue(migration.contains("('MANAGER', 'tenant.inventory.view')"));
    }
}
