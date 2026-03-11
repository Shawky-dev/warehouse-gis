INSERT INTO permissions (code, description)
VALUES
    ('tenant.warehouse.view',            'View warehouse layouts and block trees in tenant scope'),
    ('tenant.warehouse.layout.manage',   'Create and update warehouse layouts in tenant scope'),
    ('tenant.warehouse.layout.activate', 'Activate and deactivate warehouse layouts in tenant scope'),
    ('tenant.warehouse.template.manage', 'Create and update warehouse block templates in tenant scope'),
    ('tenant.warehouse.block.edit',      'Edit warehouse layout blocks in tenant scope'),
    ('tenant.warehouse.hard_delete',     'Hard-delete warehouse layout entities in tenant scope')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
SELECT rp.role_code, mapped.permission_code
FROM role_permissions rp
JOIN (
    VALUES
        ('tenant.warehouse.view', 'tenant.warehouse.view'),
        ('tenant.warehouse.edit', 'tenant.warehouse.layout.manage'),
        ('tenant.warehouse.edit', 'tenant.warehouse.layout.activate'),
        ('tenant.warehouse.edit', 'tenant.warehouse.template.manage'),
        ('tenant.warehouse.edit', 'tenant.warehouse.block.edit'),
        ('tenant.warehouse.hard_delete', 'tenant.warehouse.hard_delete')
) AS mapped(source_permission, permission_code)
    ON mapped.source_permission = rp.permission_code
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'tenant.warehouse.view'),
    ('MANAGER', 'tenant.warehouse.layout.manage'),
    ('MANAGER', 'tenant.warehouse.layout.activate'),
    ('MANAGER', 'tenant.warehouse.template.manage'),
    ('MANAGER', 'tenant.warehouse.block.edit')
ON CONFLICT (role_code, permission_code) DO NOTHING;

DELETE FROM role_permissions
WHERE permission_code IN (
    'tenant.warehouse.edit',
    'tenant.warehouse.soft_delete',
    'tenant.warehouse.restore'
);

DELETE FROM permissions
WHERE code IN (
    'tenant.warehouse.edit',
    'tenant.warehouse.soft_delete',
    'tenant.warehouse.restore'
);