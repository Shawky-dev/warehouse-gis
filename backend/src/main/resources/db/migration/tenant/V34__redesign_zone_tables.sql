-- V34: Redesign zone tables — replace hardcoded zone_type with user-created
--      gis_zones, unify buffer zones into gis_zones, introduce per-zone
--      category rules (ALLOWED | PROHIBITED).

-- ── 1. Drop old buffer-zone infrastructure ────────────────────────────────────

DROP INDEX IF EXISTS idx_gis_buffer_zones_geom;
DROP TABLE IF EXISTS gis_buffer_zones;

-- ── 2. Remove zone columns from gis_blocks ────────────────────────────────────

DROP INDEX IF EXISTS idx_gis_blocks_zone;
ALTER TABLE gis_blocks
    DROP COLUMN IF EXISTS zone_type,
    DROP COLUMN IF EXISTS allowed_category_ids;

-- ── 3. Create gis_zones ───────────────────────────────────────────────────────

CREATE TABLE gis_zones (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(200) NOT NULL,
    description      TEXT,
    geometry         GEOMETRY(Polygon, 4326) NOT NULL,
    violation_action VARCHAR(20)  NOT NULL DEFAULT 'BLOCK',
    source           VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gis_zones_geom
    ON gis_zones USING GIST (geometry);

-- ── 4. Create gis_zone_category_rules ────────────────────────────────────────

CREATE TABLE gis_zone_category_rules (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_id     UUID        NOT NULL REFERENCES gis_zones(id) ON DELETE CASCADE,
    category_id UUID        NOT NULL,
    rule_type   VARCHAR(20) NOT NULL,
    CONSTRAINT uq_zone_category UNIQUE (zone_id, category_id)
);

CREATE INDEX idx_gis_zone_rules_zone_id
    ON gis_zone_category_rules (zone_id);
