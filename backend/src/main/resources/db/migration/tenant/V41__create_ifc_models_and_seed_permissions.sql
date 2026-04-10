-- V41: Create ifc_models table and seed RBAC permissions for IFC viewer.

-- ── 1. ifc_models ────────────────────────────────────────────────────────────

CREATE TABLE ifc_models (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    original_name     VARCHAR(255) NOT NULL,
    stored_file_name  VARCHAR(255) NOT NULL,
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ifc_models_uploaded_at ON ifc_models (uploaded_at DESC);

-- ── 2. Seed RBAC permissions ──────────────────────────────────────────────────

INSERT INTO permissions (code, description) VALUES
    ('tenant.ifc.view',   'View and load 3D IFC models'),
    ('tenant.ifc.manage', 'Upload and delete IFC models')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

-- Grant both to ADMIN role.
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', p.code
  FROM permissions p
 WHERE p.code LIKE 'tenant.ifc.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;
