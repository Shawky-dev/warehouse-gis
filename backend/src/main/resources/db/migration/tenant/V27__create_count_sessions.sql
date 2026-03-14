CREATE TABLE count_sessions (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    posted_at TIMESTAMPTZ,
    posted_by VARCHAR(255),
    voided_at TIMESTAMPTZ,
    voided_by VARCHAR(255)
);

CREATE TABLE count_session_locations (
    session_id UUID NOT NULL REFERENCES count_sessions(id) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES layout_blocks(id),
    PRIMARY KEY (session_id, location_id)
);

CREATE TABLE count_lines (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES count_sessions(id) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES layout_blocks(id),
    product_id UUID NOT NULL REFERENCES products(id),
    lot_number VARCHAR(100),
    expected_qty NUMERIC(15,4) NOT NULL DEFAULT 0,
    counted_qty NUMERIC(15,4),
    variance NUMERIC(15,4) GENERATED ALWAYS AS (counted_qty - expected_qty) STORED
);

CREATE INDEX idx_count_lines_session ON count_lines(session_id);

INSERT INTO permissions (code, description)
VALUES
    ('tenant.counting.view', 'View count sessions and count lines'),
    ('tenant.counting.create', 'Open count sessions and snapshot expected stock'),
    ('tenant.counting.post', 'Post count sessions and write adjustment movements'),
    ('tenant.counting.void', 'Void open count sessions')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'ADMIN', permission.code
FROM permissions permission
WHERE permission.code LIKE 'tenant.counting.%'
ON CONFLICT (role_code, permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'tenant.counting.view'),
    ('MANAGER', 'tenant.counting.create'),
    ('MANAGER', 'tenant.counting.post'),
    ('MANAGER', 'tenant.counting.void')
ON CONFLICT (role_code, permission_code) DO NOTHING;