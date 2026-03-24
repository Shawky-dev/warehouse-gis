package com.warehouse.warehouse_platform.tenant.gis;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantGisFloorPlanMigrationContractTest {

    @Test
    void tenantChangelog_shouldReferenceFloorPlanPermissionMigration() throws Exception {
        String changelog = Files.readString(Path.of("src/main/resources/db/changelog/db.changelog-tenant.yaml"));

        assertTrue(changelog.contains("V32__seed_gis_floor_plan_permissions.sql"));
    }

    @Test
    void floorPlanPermissionMigration_shouldSeedPermissionsAndDefaultGrants() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/tenant/V32__seed_gis_floor_plan_permissions.sql"));

        assertTrue(migration.contains("gis.floorplan.view"));
        assertTrue(migration.contains("gis.floorplan.manage"));
        assertTrue(migration.contains("SELECT 'ADMIN', permission.code"));
        assertTrue(migration.contains("permission.code LIKE 'gis.floorplan.%'"));
        assertTrue(migration.contains("('MANAGER', 'gis.floorplan.view')"));
        assertTrue(migration.contains("('MANAGER', 'gis.floorplan.manage')"));
    }
}
