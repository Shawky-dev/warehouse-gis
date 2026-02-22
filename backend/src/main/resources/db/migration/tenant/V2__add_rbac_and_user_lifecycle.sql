ALTER TABLE users
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMPTZ NULL;

CREATE TABLE IF NOT EXISTS roles (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS permissions (
    code VARCHAR(100) PRIMARY KEY,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_code VARCHAR(50) NOT NULL REFERENCES roles(code) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions(code) ON DELETE CASCADE,
    PRIMARY KEY (role_code, permission_code)
);

UPDATE users
SET role = UPPER(role)
WHERE role IS NOT NULL
  AND role <> UPPER(role);

INSERT INTO roles (code, name, description)
SELECT DISTINCT role, role, 'Auto-seeded from existing users'
FROM users
WHERE role IS NOT NULL
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (code, name, description)
VALUES
    ('ADMIN', 'Administrator', 'Full tenant access'),
    ('MANAGER', 'Manager', 'Limited tenant management access')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, description)
VALUES
    ('landlord.tenants.view', 'View tenants in landlord scope'),
    ('landlord.tenants.create', 'Create tenants in landlord scope'),
    ('landlord.users.view', 'View users in landlord scope'),
    ('landlord.users.create', 'Create users in landlord scope'),
    ('landlord.users.edit', 'Edit users in landlord scope'),
    ('landlord.users.reset_password', 'Reset user passwords in landlord scope'),
    ('landlord.users.deactivate', 'Deactivate users in landlord scope')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', code
FROM permissions
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'landlord.tenants.view'),
    ('MANAGER', 'landlord.tenants.create'),
    ('MANAGER', 'landlord.users.view')
ON CONFLICT (role_code, permission_code) DO NOTHING;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS fk_users_role;

ALTER TABLE users
    ADD CONSTRAINT fk_users_role
        FOREIGN KEY (role) REFERENCES roles(code);
