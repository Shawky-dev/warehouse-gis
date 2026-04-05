-- V33: Zone management — add zone classification fields to gis_blocks,
--      create gis_buffer_zones table, seed permissions.

-- ── 1. Zone fields on gis_blocks ─────────────────────────────────────────────

ALTER TABLE gis_blocks
    ADD COLUMN zone_type            VARCHAR(50),
    ADD COLUMN allowed_category_ids UUID[] NOT NULL DEFAULT '{}';

CREATE INDEX idx_gis_blocks_zone
    ON gis_blocks (template_name)
    WHERE template_name = 'Zone';

-- ── 2. Buffer-zone table (ArcGIS Pro buffer imports) ─────────────────────────

CREATE TABLE gis_buffer_zones (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    label             VARCHAR(200),
    material_type     VARCHAR(100) NOT NULL,
    buffer_distance_m NUMERIC(8, 2),
    geometry          GEOMETRY(Polygon, 4326) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gis_buffer_zones_geom
    ON gis_buffer_zones USING GIST (geometry);

-- ── 3. Permissions ────────────────────────────────────────────────────────────

INSERT INTO permissions (code, description)
VALUES
    ('gis.zones.view',   'View GIS zone GeoJSON layers'),
    ('gis.zones.manage', 'Manage GIS zone type and category restrictions')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', permission.code
FROM permissions permission
WHERE permission.code LIKE 'gis.zones.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'gis.zones.view'),
    ('MANAGER', 'gis.zones.manage')
ON CONFLICT (role_code, permission_code) DO NOTHING;
