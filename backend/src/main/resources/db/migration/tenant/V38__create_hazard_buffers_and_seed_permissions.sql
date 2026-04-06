-- V38: Create gis_hazard_buffers and gis_hazard_buffer_restricted_hazard_types.
--      Ensure GiST indexes on all spatial columns used by validation.
--      Seed new RBAC permissions for hazard types, zone types, and hazard buffers.

-- ── 1. gis_hazard_buffers ─────────────────────────────────────────────────────

CREATE TABLE gis_hazard_buffers (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200)   NOT NULL,
    source          VARCHAR(20)    NOT NULL DEFAULT 'ARCGIS_IMPORT',
    geometry        GEOMETRY(Polygon, 4326) NOT NULL,
    notes           TEXT           NULL,
    import_batch_id UUID           NULL,
    source_filename VARCHAR(255)   NULL,
    imported_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gis_hazard_buffers_geom
    ON gis_hazard_buffers USING GIST (geometry);

CREATE INDEX idx_gis_hazard_buffers_import_batch
    ON gis_hazard_buffers (import_batch_id);

-- ── 2. gis_hazard_buffer_restricted_hazard_types ──────────────────────────────

CREATE TABLE gis_hazard_buffer_restricted_hazard_types (
    hazard_buffer_id UUID NOT NULL REFERENCES gis_hazard_buffers(id) ON DELETE CASCADE,
    hazard_type_id   UUID NOT NULL REFERENCES hazard_types(id),
    PRIMARY KEY (hazard_buffer_id, hazard_type_id)
);

CREATE INDEX idx_ghbrht_hazard_type
    ON gis_hazard_buffer_restricted_hazard_types (hazard_type_id);

-- ── 3. Ensure GiST indexes on spatial columns used by validation ──────────────

CREATE INDEX IF NOT EXISTS idx_gis_blocks_geom
    ON gis_blocks USING GIST (geometry);

-- (idx_gis_zones_geom already created in V34; idx_gis_hazard_buffers_geom above)

-- ── 4. Seed RBAC permissions ──────────────────────────────────────────────────

-- Hazard type admin permissions
INSERT INTO permissions (code, description) VALUES
    ('tenant.hazard_types.view',        'View hazard types'),
    ('tenant.hazard_types.create',      'Create hazard types'),
    ('tenant.hazard_types.edit',        'Edit hazard types'),
    ('tenant.hazard_types.deactivate',  'Deactivate hazard types'),
    ('tenant.hazard_types.reactivate',  'Reactivate hazard types'),
    ('tenant.hazard_types.hard_delete', 'Hard delete hazard types')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

-- Zone type admin permissions
INSERT INTO permissions (code, description) VALUES
    ('tenant.zone_types.view',        'View zone types'),
    ('tenant.zone_types.create',      'Create zone types'),
    ('tenant.zone_types.edit',        'Edit zone types'),
    ('tenant.zone_types.deactivate',  'Deactivate zone types'),
    ('tenant.zone_types.reactivate',  'Reactivate zone types'),
    ('tenant.zone_types.hard_delete', 'Hard delete zone types')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

-- Hazard buffer GIS permissions
INSERT INTO permissions (code, description) VALUES
    ('gis.hazard_buffers.view',   'View GIS hazard buffer layers'),
    ('gis.hazard_buffers.manage', 'Import and manage GIS hazard buffers')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

-- Grant all new permissions to ADMIN role.
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', p.code
  FROM permissions p
 WHERE p.code LIKE 'tenant.hazard_types.%'
    OR p.code LIKE 'tenant.zone_types.%'
    OR p.code LIKE 'gis.hazard_buffers.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;

-- Grant view + manage hazard-buffer permissions to MANAGER.
INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'tenant.hazard_types.view'),
    ('MANAGER', 'tenant.hazard_types.create'),
    ('MANAGER', 'tenant.hazard_types.edit'),
    ('MANAGER', 'tenant.hazard_types.deactivate'),
    ('MANAGER', 'tenant.hazard_types.reactivate'),
    ('MANAGER', 'tenant.zone_types.view'),
    ('MANAGER', 'tenant.zone_types.create'),
    ('MANAGER', 'tenant.zone_types.edit'),
    ('MANAGER', 'tenant.zone_types.deactivate'),
    ('MANAGER', 'tenant.zone_types.reactivate'),
    ('MANAGER', 'gis.hazard_buffers.view'),
    ('MANAGER', 'gis.hazard_buffers.manage')
ON CONFLICT (role_code, permission_code) DO NOTHING;
