ALTER TABLE master.users
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE master.users
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE master.users
    ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMPTZ NULL;

CREATE TABLE IF NOT EXISTS master.roles (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS master.permissions (
    code VARCHAR(100) PRIMARY KEY,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS master.role_permissions (
    role_code VARCHAR(50) NOT NULL REFERENCES master.roles(code) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL REFERENCES master.permissions(code) ON DELETE CASCADE,
    PRIMARY KEY (role_code, permission_code)
);

UPDATE master.users
SET role = UPPER(role)
WHERE role IS NOT NULL
  AND role <> UPPER(role);

INSERT INTO master.roles (code, name, description)
SELECT DISTINCT role, role, 'Auto-seeded from existing users'
FROM master.users
WHERE role IS NOT NULL
ON CONFLICT (code) DO NOTHING;

INSERT INTO master.roles (code, name, description)
VALUES
    ('ADMIN', 'Administrator', 'Full landlord access'),
    ('MANAGER', 'Manager', 'Limited landlord management access')
ON CONFLICT (code) DO NOTHING;

INSERT INTO master.permissions (code, description)
VALUES
    ('landlord.tenants.view', 'View tenants in landlord scope'),
    ('landlord.tenants.create', 'Create tenants in landlord scope'),
    ('landlord.users.view', 'View users in landlord scope'),
    ('landlord.users.create', 'Create users in landlord scope'),
    ('landlord.users.edit', 'Edit users in landlord scope'),
    ('landlord.users.reset_password', 'Reset user passwords in landlord scope'),
    ('landlord.users.deactivate', 'Deactivate users in landlord scope')
ON CONFLICT (code) DO NOTHING;

INSERT INTO master.role_permissions (role_code, permission_code)
SELECT 'ADMIN', code
FROM master.permissions
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO master.role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'landlord.tenants.view'),
    ('MANAGER', 'landlord.tenants.create'),
    ('MANAGER', 'landlord.users.view')
ON CONFLICT (role_code, permission_code) DO NOTHING;

ALTER TABLE master.users
    DROP CONSTRAINT IF EXISTS fk_users_role;

ALTER TABLE master.users
    ADD CONSTRAINT fk_users_role
        FOREIGN KEY (role) REFERENCES master.roles(code);
