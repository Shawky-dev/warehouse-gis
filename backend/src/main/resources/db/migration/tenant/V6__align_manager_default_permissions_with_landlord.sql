-- Align tenant MANAGER defaults with landlord MANAGER-style access.
-- Keep MANAGER as limited access (not ADMIN parity).
DELETE FROM role_permissions
WHERE role_code = 'MANAGER'
  AND permission_code IN (
    'tenant.users.create',
    'tenant.users.edit',
    'tenant.users.reset_password',
    'tenant.users.deactivate',
    'tenant.users.reactivate',
    'tenant.roles.edit'
  );

INSERT INTO role_permissions (role_code, permission_code)
VALUES ('MANAGER', 'tenant.users.view')
ON CONFLICT (role_code, permission_code) DO NOTHING;
