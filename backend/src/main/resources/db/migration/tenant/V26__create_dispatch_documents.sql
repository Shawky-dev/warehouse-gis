CREATE TABLE dispatch_documents (
    id UUID PRIMARY KEY,
    destination VARCHAR(200),
    reference VARCHAR(120),
    notes VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    posted_at TIMESTAMPTZ,
    posted_by VARCHAR(255),
    voided_at TIMESTAMPTZ,
    voided_by VARCHAR(255)
);

CREATE TABLE dispatch_lines (
    id UUID PRIMARY KEY,
    dispatch_id UUID NOT NULL REFERENCES dispatch_documents(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    source_location_id UUID NOT NULL REFERENCES layout_blocks(id),
    qty NUMERIC(15,4) NOT NULL CHECK (qty > 0),
    lot_number VARCHAR(100),
    notes VARCHAR(500),
    position INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_dispatch_lines_dispatch ON dispatch_lines(dispatch_id);

INSERT INTO permissions (code, description)
VALUES
    ('tenant.dispatches.view', 'View dispatch documents and lines'),
    ('tenant.dispatches.create', 'Create dispatch drafts'),
    ('tenant.dispatches.edit', 'Add, update, and remove dispatch lines'),
    ('tenant.dispatches.post', 'Post dispatch drafts to inventory ledger'),
    ('tenant.dispatches.void', 'Void posted dispatch documents')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', permission.code
FROM permissions permission
WHERE permission.code LIKE 'tenant.dispatches.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'tenant.dispatches.view'),
    ('MANAGER', 'tenant.dispatches.create'),
    ('MANAGER', 'tenant.dispatches.edit'),
    ('MANAGER', 'tenant.dispatches.post'),
    ('MANAGER', 'tenant.dispatches.void')
ON CONFLICT (role_code, permission_code) DO NOTHING;
