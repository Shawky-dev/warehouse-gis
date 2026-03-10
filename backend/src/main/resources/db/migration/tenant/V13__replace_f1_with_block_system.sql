-- ============================================================
-- V13: Replace rigid warehouse hierarchy with block-based system
-- Drops: warehouse_shelves, warehouse_bay_levels, warehouse_bays,
--        warehouse_aisle_sides, warehouse_aisles, warehouse_layouts
-- Creates: block_templates, warehouse_layouts, layout_blocks
-- ============================================================

-- Drop old tables (reverse dependency order)
DROP TABLE IF EXISTS warehouse_shelves;
DROP TABLE IF EXISTS warehouse_bay_levels;
DROP TABLE IF EXISTS warehouse_bays;
DROP TABLE IF EXISTS warehouse_aisle_sides;
DROP TABLE IF EXISTS warehouse_aisles;
DROP TABLE IF EXISTS warehouse_layouts;

-- ============================================================
-- block_templates
-- Reusable type definitions (e.g. "Aisle", "Bay", "Shelf").
-- Shared across layouts; a template can appear in multiple layouts.
-- ============================================================
CREATE TABLE block_templates (
    id                UUID        PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    identifier_format VARCHAR(20)  NOT NULL
        CHECK (identifier_format IN ('NUMERIC', 'ALPHA', 'CUSTOM', 'FREE_TEXT')),
    side_config       VARCHAR(20)  NOT NULL DEFAULT 'NONE'
        CHECK (side_config IN ('NONE', 'LR', 'AB', 'CUSTOM')),
    -- Comma-separated values used when side_config = 'CUSTOM' (e.g. 'North,South,East,West')
    side_options      VARCHAR(500) NULL,
    required          BOOLEAN      NOT NULL DEFAULT TRUE,
    description       VARCHAR(500) NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_block_templates_name_ci ON block_templates ((LOWER(name)));
CREATE INDEX idx_block_templates_format ON block_templates (identifier_format);

-- ============================================================
-- warehouse_layouts
-- Named blueprint/configuration of a warehouse structure.
-- Exactly ONE layout may be active at a time (partial unique index).
-- ============================================================
CREATE TABLE warehouse_layouts (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_warehouse_layouts_name_ci ON warehouse_layouts ((LOWER(name)));
-- Enforce single-active-layout constraint at the DB level
CREATE UNIQUE INDEX uq_warehouse_layouts_one_active ON warehouse_layouts (is_active)
    WHERE is_active = TRUE;

-- ============================================================
-- layout_blocks
-- Ordered, nested tree of block templates within a layout.
-- parent_id = NULL means root level.
-- position is the ordering index within a parent (0-based).
-- ============================================================
CREATE TABLE layout_blocks (
    id                UUID        PRIMARY KEY,
    layout_id         UUID        NOT NULL REFERENCES warehouse_layouts(id) ON DELETE CASCADE,
    block_template_id UUID        NOT NULL REFERENCES block_templates(id),
    parent_id         UUID        NULL REFERENCES layout_blocks(id) ON DELETE CASCADE,
    position          INTEGER     NOT NULL CHECK (position >= 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique position within a parent (handles both root and non-root via two partial indexes)
CREATE UNIQUE INDEX uq_layout_blocks_position_rooted
    ON layout_blocks (layout_id, parent_id, position)
    WHERE parent_id IS NOT NULL;

CREATE UNIQUE INDEX uq_layout_blocks_position_root
    ON layout_blocks (layout_id, position)
    WHERE parent_id IS NULL;

CREATE INDEX idx_layout_blocks_layout    ON layout_blocks (layout_id);
CREATE INDEX idx_layout_blocks_parent    ON layout_blocks (layout_id, parent_id);
CREATE INDEX idx_layout_blocks_template  ON layout_blocks (block_template_id);
