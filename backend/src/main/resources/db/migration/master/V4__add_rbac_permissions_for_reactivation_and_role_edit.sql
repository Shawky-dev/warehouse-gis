INSERT INTO master.permissions (code, description)
VALUES
    ('landlord.users.reactivate', 'Reactivate users in landlord scope'),
    ('landlord.roles.edit', 'View and edit roles in landlord scope')
ON CONFLICT (code) DO NOTHING;

INSERT INTO master.role_permissions (role_code, permission_code)
VALUES
    ('ADMIN', 'landlord.users.reactivate'),
    ('ADMIN', 'landlord.roles.edit')
ON CONFLICT (role_code, permission_code) DO NOTHING;
