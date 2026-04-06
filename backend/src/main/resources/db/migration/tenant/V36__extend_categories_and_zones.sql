-- V36: Extend product_categories with code / display_name / required_zone_type_id.
--      Extend gis_zones with zone_type_id / display_color.

-- ── 1. product_categories extensions ─────────────────────────────────────────

ALTER TABLE product_categories
    ADD COLUMN code                 VARCHAR(60)  NULL,
    ADD COLUMN display_name         VARCHAR(120) NULL,
    ADD COLUMN required_zone_type_id UUID        NULL REFERENCES zone_types(id);

-- Backfill existing rows: display_name = name, code = uppercased-slugified name.
UPDATE product_categories
   SET display_name = name,
       code = UPPER(REGEXP_REPLACE(TRIM(name), '[^A-Za-z0-9]+', '_', 'g'))
 WHERE code IS NULL;

-- Make code / display_name NOT NULL now that they are backfilled.
ALTER TABLE product_categories
    ALTER COLUMN code         SET NOT NULL,
    ALTER COLUMN display_name SET NOT NULL;

CREATE UNIQUE INDEX uq_product_categories_code
    ON product_categories (code);

CREATE INDEX idx_product_categories_required_zone_type
    ON product_categories (required_zone_type_id);

-- Ensure a STANDARD row exists (no required zone type).
INSERT INTO product_categories (id, name, code, display_name, active)
VALUES (gen_random_uuid(), 'Standard', 'STANDARD', 'Standard', TRUE)
ON CONFLICT DO NOTHING;

-- Ensure a PERISHABLE row exists pointing to REFRIGERATED.
WITH zt AS (SELECT id FROM zone_types WHERE code = 'REFRIGERATED')
INSERT INTO product_categories (id, name, code, display_name, active, required_zone_type_id)
SELECT gen_random_uuid(), 'Perishable', 'PERISHABLE', 'Perishable', TRUE, zt.id
  FROM zt
ON CONFLICT DO NOTHING;

-- ── 2. gis_zones extensions ───────────────────────────────────────────────────

ALTER TABLE gis_zones
    ADD COLUMN zone_type_id   UUID         NULL REFERENCES zone_types(id),
    ADD COLUMN display_color  VARCHAR(7)   NULL;

-- Backfill existing zones with a neutral default colour.
UPDATE gis_zones SET display_color = '#6B7280' WHERE display_color IS NULL;

CREATE INDEX idx_gis_zones_zone_type
    ON gis_zones (zone_type_id);
