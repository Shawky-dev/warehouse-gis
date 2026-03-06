INSERT INTO permissions (code, description)
VALUES
    ('tenant.users.view', 'View users in tenant scope'),
    ('tenant.users.create', 'Create users in tenant scope'),
    ('tenant.users.edit', 'Edit users in tenant scope'),
    ('tenant.users.reset_password', 'Reset user passwords in tenant scope'),
    ('tenant.users.deactivate', 'Deactivate users in tenant scope'),
    ('tenant.users.reactivate', 'Reactivate users in tenant scope'),
    ('tenant.roles.edit', 'View and edit roles in tenant scope')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', code
FROM permissions
WHERE code LIKE 'tenant.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'tenant.users.view'),
    ('MANAGER', 'tenant.users.create'),
    ('MANAGER', 'tenant.users.edit'),
    ('MANAGER', 'tenant.users.reset_password'),
    ('MANAGER', 'tenant.users.deactivate'),
    ('MANAGER', 'tenant.users.reactivate'),
    ('MANAGER', 'tenant.roles.edit')
ON CONFLICT (role_code, permission_code) DO NOTHING;

DELETE FROM role_permissions
WHERE permission_code IN (
    'landlord.tenants.view',
    'landlord.tenants.create',
    'landlord.users.view',
    'landlord.users.create',
    'landlord.users.edit',
    'landlord.users.reset_password',
    'landlord.users.deactivate',
    'landlord.users.reactivate',
    'landlord.roles.edit'
);

DELETE FROM permissions
WHERE code IN (
    'landlord.tenants.view',
    'landlord.tenants.create',
    'landlord.users.view',
    'landlord.users.create',
    'landlord.users.edit',
    'landlord.users.reset_password',
    'landlord.users.deactivate',
    'landlord.users.reactivate',
    'landlord.roles.edit'
);
