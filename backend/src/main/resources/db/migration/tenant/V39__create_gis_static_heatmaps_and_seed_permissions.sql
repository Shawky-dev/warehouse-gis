-- V39: Create gis_static_heatmaps and seed GIS heatmap RBAC permissions.

-- ── 1. gis_static_heatmaps ────────────────────────────────────────────────────

CREATE TABLE gis_static_heatmaps (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     VARCHAR(120) NOT NULL,
    source_filename          VARCHAR(255) NOT NULL,
    content_type             VARCHAR(100) NOT NULL,
    geoserver_coverage_store VARCHAR(160) NOT NULL UNIQUE,
    geoserver_layer_name     VARCHAR(160) NOT NULL UNIQUE,
    publish_status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    is_default               BOOLEAN      NOT NULL DEFAULT FALSE,
    uploaded_by              VARCHAR(255) NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- At most one active default per tenant schema.
CREATE UNIQUE INDEX idx_gis_static_heatmaps_one_default
    ON gis_static_heatmaps (is_default)
    WHERE is_default = TRUE AND publish_status = 'ACTIVE';

-- Fast lookup of active rows.
CREATE INDEX idx_gis_static_heatmaps_active
    ON gis_static_heatmaps (publish_status)
    WHERE publish_status = 'ACTIVE';

-- ── 2. Seed RBAC permissions ──────────────────────────────────────────────────

INSERT INTO permissions (code, description) VALUES
    ('gis.heatmaps.view',   'View and consume GIS heatmap layers'),
    ('gis.heatmaps.manage', 'Upload and manage GIS static heatmap layers')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

-- Grant both permissions to ADMIN.
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', p.code
  FROM permissions p
 WHERE p.code LIKE 'gis.heatmaps.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;

-- Grant view + manage to MANAGER.
INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'gis.heatmaps.view'),
    ('MANAGER', 'gis.heatmaps.manage')
ON CONFLICT (role_code, permission_code) DO NOTHING;
