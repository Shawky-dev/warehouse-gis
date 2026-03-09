package com.warehouse.warehouse_platform.tenant.product;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantF0MigrationContractTest {

    @Test
    void tenantChangelog_shouldReferenceF0Migrations() throws Exception {
        String changelog = Files.readString(Path.of("src/main/resources/db/changelog/db.changelog-tenant.yaml"));

        assertTrue(changelog.contains("V7__create_f0_master_data_and_audit_tables.sql"));
        assertTrue(changelog.contains("V8__seed_f0_permissions_and_manager_defaults.sql"));
    }

    @Test
    void f0SchemaMigration_shouldContainCoreTablesAndIndexes() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/tenant/V7__create_f0_master_data_and_audit_tables.sql"));

        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS units_of_measure"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS suppliers"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS products"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS product_suppliers"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS audit_log"));
        assertTrue(migration.contains("uq_product_suppliers_single_primary"));
    }

    @Test
    void f0PermissionMigration_shouldContainHardDeleteAndAuditPermissions() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/tenant/V8__seed_f0_permissions_and_manager_defaults.sql"));

        assertTrue(migration.contains("tenant.uoms.hard_delete"));
        assertTrue(migration.contains("tenant.suppliers.hard_delete"));
        assertTrue(migration.contains("tenant.products.hard_delete"));
        assertTrue(migration.contains("tenant.audit.view"));
    }
}
