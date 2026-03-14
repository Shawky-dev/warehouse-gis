-- ============================================================
-- V25: Backfill receipt permission grants for existing ADMIN roles
-- Keeps tenant RBAC parity with earlier feature rollouts where ADMIN
-- receives all new feature permissions by default.
-- ============================================================

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', permission.code
FROM permissions permission
WHERE permission.code LIKE 'tenant.receipts.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;
