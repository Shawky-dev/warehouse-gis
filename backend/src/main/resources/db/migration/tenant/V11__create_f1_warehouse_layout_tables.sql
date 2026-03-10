CREATE TABLE IF NOT EXISTS warehouse_layouts (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMPTZ NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_layouts_code_ci ON warehouse_layouts ((LOWER(code)));
CREATE INDEX IF NOT EXISTS idx_warehouse_layouts_active ON warehouse_layouts (active, name);

CREATE TABLE IF NOT EXISTS warehouse_aisles (
    id UUID PRIMARY KEY,
    layout_id UUID NOT NULL REFERENCES warehouse_layouts(id),
    code VARCHAR(20) NOT NULL,
    name VARCHAR(120),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMPTZ NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_aisles_layout_code_ci ON warehouse_aisles (layout_id, (LOWER(code)));
CREATE INDEX IF NOT EXISTS idx_warehouse_aisles_layout ON warehouse_aisles (layout_id);
CREATE INDEX IF NOT EXISTS idx_warehouse_aisles_active ON warehouse_aisles (active);

CREATE TABLE IF NOT EXISTS warehouse_aisle_sides (
    id UUID PRIMARY KEY,
    aisle_id UUID NOT NULL REFERENCES warehouse_aisles(id),
    side CHAR(1) NOT NULL CHECK (side IN ('L', 'R')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMPTZ NULL,
    UNIQUE (aisle_id, side)
);
CREATE INDEX IF NOT EXISTS idx_warehouse_aisle_sides_aisle ON warehouse_aisle_sides (aisle_id);

CREATE TABLE IF NOT EXISTS warehouse_bays (
    id UUID PRIMARY KEY,
    side_id UUID NOT NULL REFERENCES warehouse_aisle_sides(id),
    code VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMPTZ NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_bays_side_code_ci ON warehouse_bays (side_id, (LOWER(code)));
CREATE INDEX IF NOT EXISTS idx_warehouse_bays_side ON warehouse_bays (side_id);

CREATE TABLE IF NOT EXISTS warehouse_bay_levels (
    id UUID PRIMARY KEY,
    bay_id UUID NOT NULL REFERENCES warehouse_bays(id),
    level_num INTEGER NOT NULL CHECK (level_num >= 1),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMPTZ NULL,
    UNIQUE (bay_id, level_num)
);
CREATE INDEX IF NOT EXISTS idx_warehouse_bay_levels_bay ON warehouse_bay_levels (bay_id);

CREATE TABLE IF NOT EXISTS warehouse_shelves (
    id UUID PRIMARY KEY,
    level_id UUID NOT NULL REFERENCES warehouse_bay_levels(id),
    shelf_num INTEGER NOT NULL CHECK (shelf_num >= 1),
    location_code VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMPTZ NULL,
    UNIQUE (level_id, shelf_num)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_shelves_location_code_ci ON warehouse_shelves ((LOWER(location_code)));
CREATE INDEX IF NOT EXISTS idx_warehouse_shelves_level ON warehouse_shelves (level_id);
