INSERT INTO permissions (code, description)
VALUES
    ('tenant.categories.view',        'View product categories in tenant scope'),
    ('tenant.categories.create',      'Create product categories in tenant scope'),
    ('tenant.categories.edit',        'Edit product categories in tenant scope'),
    ('tenant.categories.soft_delete', 'Soft-delete product categories in tenant scope'),
    ('tenant.categories.restore',     'Restore product categories in tenant scope'),
    ('tenant.categories.hard_delete', 'Hard-delete product categories in tenant scope')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', code
FROM permissions
WHERE code LIKE 'tenant.categories.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'tenant.categories.view'),
    ('MANAGER', 'tenant.categories.create'),
    ('MANAGER', 'tenant.categories.edit'),
    ('MANAGER', 'tenant.categories.soft_delete'),
    ('MANAGER', 'tenant.categories.restore')
ON CONFLICT (role_code, permission_code) DO NOTHING;
