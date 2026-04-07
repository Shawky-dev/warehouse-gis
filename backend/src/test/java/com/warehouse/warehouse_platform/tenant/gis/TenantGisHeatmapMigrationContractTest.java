package com.warehouse.warehouse_platform.tenant.gis;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantGisHeatmapMigrationContractTest {

    @Test
    void tenantChangelog_shouldReferenceHeatmapMigration() throws Exception {
        String changelog = Files.readString(Path.of("src/main/resources/db/changelog/db.changelog-tenant.yaml"));

        assertTrue(changelog.contains("V39__create_gis_static_heatmaps_and_seed_permissions.sql"),
                "Changelog must reference the V39 heatmap migration file");
    }

    @Test
    void heatmapMigration_shouldCreateTableWithExpectedColumns() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/tenant/V39__create_gis_static_heatmaps_and_seed_permissions.sql"));

        assertTrue(migration.contains("CREATE TABLE gis_static_heatmaps"));
        assertTrue(migration.contains("id"));
        assertTrue(migration.contains("name"));
        assertTrue(migration.contains("source_filename"));
        assertTrue(migration.contains("content_type"));
        assertTrue(migration.contains("geoserver_coverage_store"));
        assertTrue(migration.contains("geoserver_layer_name"));
        assertTrue(migration.contains("publish_status"));
        assertTrue(migration.contains("DEFAULT 'ACTIVE'"));
        assertTrue(migration.contains("is_default"));
        assertTrue(migration.contains("uploaded_by"));
        assertTrue(migration.contains("created_at"));
        assertTrue(migration.contains("updated_at"));
    }

    @Test
    void heatmapMigration_shouldCreatePartialUniqueIndexForDefault() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/tenant/V39__create_gis_static_heatmaps_and_seed_permissions.sql"));

        assertTrue(migration.contains("CREATE UNIQUE INDEX"),
                "Partial unique index for is_default must be present");
        assertTrue(migration.contains("is_default = TRUE AND publish_status = 'ACTIVE'"),
                "Index WHERE clause must restrict to active defaults only");
    }

    @Test
    void heatmapMigration_shouldSeedPermissionsAndDefaultGrants() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/tenant/V39__create_gis_static_heatmaps_and_seed_permissions.sql"));

        assertTrue(migration.contains("gis.heatmaps.view"));
        assertTrue(migration.contains("gis.heatmaps.manage"));
        assertTrue(migration.contains("SELECT 'ADMIN', p.code"));
        assertTrue(migration.contains("p.code LIKE 'gis.heatmaps.%'"));
        assertTrue(migration.contains("('MANAGER', 'gis.heatmaps.view')"));
        assertTrue(migration.contains("('MANAGER', 'gis.heatmaps.manage')"));
    }
}
