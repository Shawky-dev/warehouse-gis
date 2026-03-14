CREATE TABLE receipt_documents (
    id UUID PRIMARY KEY,
    supplier_id UUID REFERENCES suppliers(id),
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

CREATE TABLE receipt_lines (
    id UUID PRIMARY KEY,
    receipt_id UUID NOT NULL REFERENCES receipt_documents(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    destination_location_id UUID NOT NULL REFERENCES layout_blocks(id),
    qty NUMERIC(15,4) NOT NULL CHECK (qty > 0),
    lot_number VARCHAR(100),
    expiry_date DATE,
    notes VARCHAR(500),
    position INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_receipt_lines_receipt ON receipt_lines(receipt_id);
CREATE INDEX idx_receipt_docs_status ON receipt_documents(status);

INSERT INTO permissions (code, description)
VALUES
    ('tenant.receipts.view', 'View receipt documents and lines'),
    ('tenant.receipts.create', 'Create receipt drafts'),
    ('tenant.receipts.edit', 'Add, update, and remove receipt lines'),
    ('tenant.receipts.post', 'Post receipt drafts to inventory ledger'),
    ('tenant.receipts.void', 'Void posted receipts')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'tenant.receipts.view'),
    ('MANAGER', 'tenant.receipts.create'),
    ('MANAGER', 'tenant.receipts.edit'),
    ('MANAGER', 'tenant.receipts.post'),
    ('MANAGER', 'tenant.receipts.void')
ON CONFLICT (role_code, permission_code) DO NOTHING;