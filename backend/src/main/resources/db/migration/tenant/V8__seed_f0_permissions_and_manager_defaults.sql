INSERT INTO permissions (code, description)
VALUES
    ('tenant.uoms.view', 'View units of measure in tenant scope'),
    ('tenant.uoms.create', 'Create units of measure in tenant scope'),
    ('tenant.uoms.edit', 'Edit units of measure in tenant scope'),
    ('tenant.uoms.soft_delete', 'Soft-delete units of measure in tenant scope'),
    ('tenant.uoms.restore', 'Restore units of measure in tenant scope'),
    ('tenant.uoms.hard_delete', 'Hard-delete units of measure in tenant scope'),

    ('tenant.suppliers.view', 'View suppliers in tenant scope'),
    ('tenant.suppliers.create', 'Create suppliers in tenant scope'),
    ('tenant.suppliers.edit', 'Edit suppliers in tenant scope'),
    ('tenant.suppliers.soft_delete', 'Soft-delete suppliers in tenant scope'),
    ('tenant.suppliers.restore', 'Restore suppliers in tenant scope'),
    ('tenant.suppliers.hard_delete', 'Hard-delete suppliers in tenant scope'),

    ('tenant.products.view', 'View products in tenant scope'),
    ('tenant.products.create', 'Create products in tenant scope'),
    ('tenant.products.edit', 'Edit products in tenant scope'),
    ('tenant.products.soft_delete', 'Soft-delete products in tenant scope'),
    ('tenant.products.restore', 'Restore products in tenant scope'),
    ('tenant.products.hard_delete', 'Hard-delete products in tenant scope'),

    ('tenant.audit.view', 'View tenant audit log')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', code
FROM permissions
WHERE code LIKE 'tenant.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'tenant.uoms.view'),
    ('MANAGER', 'tenant.uoms.create'),
    ('MANAGER', 'tenant.uoms.edit'),
    ('MANAGER', 'tenant.uoms.soft_delete'),
    ('MANAGER', 'tenant.uoms.restore'),

    ('MANAGER', 'tenant.suppliers.view'),
    ('MANAGER', 'tenant.suppliers.create'),
    ('MANAGER', 'tenant.suppliers.edit'),
    ('MANAGER', 'tenant.suppliers.soft_delete'),
    ('MANAGER', 'tenant.suppliers.restore'),

    ('MANAGER', 'tenant.products.view'),
    ('MANAGER', 'tenant.products.create'),
    ('MANAGER', 'tenant.products.edit'),
    ('MANAGER', 'tenant.products.soft_delete'),
    ('MANAGER', 'tenant.products.restore'),

    ('MANAGER', 'tenant.audit.view')
ON CONFLICT (role_code, permission_code) DO NOTHING;
