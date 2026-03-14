-- ============================================================
-- V17: F2 – Inventory Ledger Engine
-- Creates: stock_movements table, v_stock view, inventory permissions
-- ============================================================

-- ============================================================
-- stock_movements
-- Immutable ledger. Every stock change is a row here.
-- qty is always a signed delta relative to the location:
--   positive = stock entering the location
--   negative = stock leaving the location
--
-- Movement types:
--   RECEIVE      – inbound stock arriving at a location
--   TRANSFER_IN  – stock arriving from another location (paired with TRANSFER_OUT)
--   TRANSFER_OUT – stock leaving to another location   (paired with TRANSFER_IN)
--   ADJUST       – manual correction (positive or negative)
--   PICK         – stock removed for outbound (always negative)
--
-- reference_id links a TRANSFER_OUT row to its TRANSFER_IN counterpart.
-- ============================================================
CREATE TABLE stock_movements (
    id            UUID            PRIMARY KEY,
    location_id   UUID            NOT NULL REFERENCES layout_blocks(id),
    product_id    UUID            NOT NULL REFERENCES products(id),
    qty           NUMERIC(15, 4)  NOT NULL,
    type          VARCHAR(20)     NOT NULL
        CHECK (type IN ('RECEIVE', 'TRANSFER_IN', 'TRANSFER_OUT', 'ADJUST', 'PICK')),
    -- Links the two rows of a transfer pair (same value on both sides)
    reference_id  UUID            NULL,
    lot_number    VARCHAR(100)    NULL,
    expiry_date   DATE            NULL,
    notes         VARCHAR(500)    NULL,
    created_by    VARCHAR(255)    NOT NULL,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Immutable: no UPDATE or DELETE should ever happen — no updated_at column by design.

CREATE INDEX idx_stock_movements_location   ON stock_movements (location_id);
CREATE INDEX idx_stock_movements_product    ON stock_movements (product_id);
CREATE INDEX idx_stock_movements_type       ON stock_movements (type);
CREATE INDEX idx_stock_movements_created_at ON stock_movements (created_at DESC);
CREATE INDEX idx_stock_movements_reference  ON stock_movements (reference_id) WHERE reference_id IS NOT NULL;
-- Composite index for stock aggregation queries
CREATE INDEX idx_stock_movements_loc_prod   ON stock_movements (location_id, product_id);

-- ============================================================
-- v_stock
-- Derived current stock level per (location, product).
-- Rows with qty_stock = 0 are excluded (location is empty).
-- This view is the single source of truth for current inventory.
-- ============================================================
CREATE OR REPLACE VIEW v_stock AS
SELECT
    location_id,
    product_id,
    SUM(qty) AS qty_stock
FROM stock_movements
GROUP BY location_id, product_id
HAVING SUM(qty) <> 0;

-- ============================================================
-- Inventory permissions
-- ============================================================
INSERT INTO permissions (code, description)
VALUES
    ('tenant.inventory.view',     'View current stock levels and movement history'),
    ('tenant.inventory.receive',  'Record inbound stock receipts'),
    ('tenant.inventory.transfer', 'Transfer stock between locations'),
    ('tenant.inventory.adjust',   'Perform manual stock adjustments')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

-- Grant all inventory permissions to the MANAGER role by default
INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('MANAGER', 'tenant.inventory.view'),
    ('MANAGER', 'tenant.inventory.receive'),
    ('MANAGER', 'tenant.inventory.transfer'),
    ('MANAGER', 'tenant.inventory.adjust')
ON CONFLICT (role_code, permission_code) DO NOTHING;
