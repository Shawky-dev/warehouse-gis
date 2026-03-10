INSERT INTO permissions (code, description)
VALUES
    ('tenant.warehouse.view',        'View warehouse layout in tenant scope'),
    ('tenant.warehouse.edit',        'Create and edit warehouse layout entities in tenant scope'),
    ('tenant.warehouse.soft_delete', 'Soft-delete warehouse layout entities in tenant scope'),
    ('tenant.warehouse.restore',     'Restore warehouse layout entities in tenant scope'),
    ('tenant.warehouse.hard_delete', 'Hard-delete warehouse layout entities in tenant scope')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', code
FROM permissions
WHERE code LIKE 'tenant.warehouse.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'tenant.warehouse.view'),
    ('MANAGER', 'tenant.warehouse.edit'),
    ('MANAGER', 'tenant.warehouse.soft_delete'),
    ('MANAGER', 'tenant.warehouse.restore')
ON CONFLICT (role_code, permission_code) DO NOTHING;
