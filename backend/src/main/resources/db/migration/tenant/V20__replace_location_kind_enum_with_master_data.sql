CREATE TABLE IF NOT EXISTS warehouse_location_kinds (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_location_kinds_name_ci
    ON warehouse_location_kinds ((LOWER(name)));
CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_location_kinds_sort_order
    ON warehouse_location_kinds (sort_order);

INSERT INTO warehouse_location_kinds (id, name, sort_order)
VALUES
    ('41000000-0000-0000-0000-000000000001', 'Storage', 0),
    ('41000000-0000-0000-0000-000000000002', 'Staging', 1),
    ('41000000-0000-0000-0000-000000000003', 'Quarantine', 2),
    ('41000000-0000-0000-0000-000000000004', 'Damaged', 3),
    ('41000000-0000-0000-0000-000000000005', 'Dispatch', 4),
    ('41000000-0000-0000-0000-000000000006', 'Structural', 5)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE layout_blocks
    ADD COLUMN location_kind_id UUID;

UPDATE layout_blocks
SET location_kind_id = CASE location_kind
    WHEN 'STORAGE' THEN '41000000-0000-0000-0000-000000000001'::UUID
    WHEN 'STAGING' THEN '41000000-0000-0000-0000-000000000002'::UUID
    WHEN 'QUARANTINE' THEN '41000000-0000-0000-0000-000000000003'::UUID
    WHEN 'DAMAGED' THEN '41000000-0000-0000-0000-000000000004'::UUID
    WHEN 'DISPATCH' THEN '41000000-0000-0000-0000-000000000005'::UUID
    WHEN 'STRUCTURAL' THEN '41000000-0000-0000-0000-000000000006'::UUID
    ELSE '41000000-0000-0000-0000-000000000001'::UUID
END
WHERE location_kind_id IS NULL;

ALTER TABLE layout_blocks
    ALTER COLUMN location_kind_id SET NOT NULL;

ALTER TABLE layout_blocks
    ADD CONSTRAINT fk_layout_blocks_location_kind
    FOREIGN KEY (location_kind_id) REFERENCES warehouse_location_kinds(id);

CREATE INDEX IF NOT EXISTS idx_layout_blocks_location_kind_id
    ON layout_blocks (location_kind_id);

ALTER TABLE layout_blocks
    DROP COLUMN location_kind;
