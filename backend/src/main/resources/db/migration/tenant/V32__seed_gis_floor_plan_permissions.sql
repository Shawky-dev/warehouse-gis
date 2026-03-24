INSERT INTO permissions (code, description)
VALUES
    ('gis.floorplan.view', 'View GIS floor plans'),
    ('gis.floorplan.manage', 'Upload and remove GIS floor plans')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', permission.code
FROM permissions permission
WHERE permission.code LIKE 'gis.floorplan.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'gis.floorplan.view'),
    ('MANAGER', 'gis.floorplan.manage')
ON CONFLICT (role_code, permission_code) DO NOTHING;
