-- ============================================================
-- V18: Backfill inventory permission grants for existing roles
-- Keeps the inventory ledger rollout aligned with the tenant RBAC pattern:
-- new tenant feature permissions belong to ADMIN by default, while MANAGER
-- receives the scoped operational subset explicitly.
-- ============================================================

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', permission.code
FROM permissions permission
WHERE permission.code LIKE 'tenant.inventory.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'tenant.inventory.view'),
    ('MANAGER', 'tenant.inventory.receive'),
    ('MANAGER', 'tenant.inventory.transfer'),
    ('MANAGER', 'tenant.inventory.adjust')
ON CONFLICT (role_code, permission_code) DO NOTHING;
