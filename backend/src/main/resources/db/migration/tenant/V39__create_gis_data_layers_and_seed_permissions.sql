-- V39: Create gis_data_layers table and seed RBAC permissions for data layers.

-- ── 1. gis_data_layers ───────────────────────────────────────────────────────

CREATE TABLE gis_data_layers (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(200)  NOT NULL,
    file_name        VARCHAR(255)  NOT NULL,
    geoserver_layer  VARCHAR(255)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gis_data_layers_name ON gis_data_layers (name);

-- ── 2. Seed RBAC permissions ──────────────────────────────────────────────────

INSERT INTO permissions (code, description) VALUES
    ('gis.data_layers.view',   'View GIS data layers'),
    ('gis.data_layers.manage', 'Upload and manage GIS data layers')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

-- Grant both to ADMIN role.
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', p.code
  FROM permissions p
 WHERE p.code LIKE 'gis.data_layers.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;
