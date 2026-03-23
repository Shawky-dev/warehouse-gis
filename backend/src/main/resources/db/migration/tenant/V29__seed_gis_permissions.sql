INSERT INTO permissions (code, description)
VALUES
    ('gis.layout.regenerate', 'Regenerate GIS shadow tables from the active warehouse layout'),
    ('gis.layout.view', 'Export GIS layout data and query GeoServer layers')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('ADMIN', 'gis.layout.regenerate'),
    ('ADMIN', 'gis.layout.view'),
    ('MANAGER', 'gis.layout.regenerate'),
    ('MANAGER', 'gis.layout.view')
ON CONFLICT (role_code, permission_code) DO NOTHING;
