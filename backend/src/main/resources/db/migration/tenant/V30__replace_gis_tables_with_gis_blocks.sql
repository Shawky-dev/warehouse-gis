-- Drop old hardcoded shadow tables if they exist (created by V28)
DROP TABLE IF EXISTS gis_shelves;
DROP TABLE IF EXISTS gis_aisles;
DROP TABLE IF EXISTS gis_zones;

-- Unified shadow table: one row per layout block, template_name drives GeoServer layer assignment
CREATE TABLE gis_blocks (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    layout_block_id  UUID NOT NULL REFERENCES layout_blocks(id) ON DELETE CASCADE,
    template_name    VARCHAR(100) NOT NULL,
    label            VARCHAR(200),
    position_path    TEXT,
    geometry         GEOMETRY(Polygon, 4326) NOT NULL,
    centroid_geom    GEOMETRY(Point, 4326),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gis_blocks_geom     ON gis_blocks USING GIST (geometry);
CREATE INDEX idx_gis_blocks_block    ON gis_blocks (layout_block_id);
CREATE INDEX idx_gis_blocks_template ON gis_blocks (template_name);
