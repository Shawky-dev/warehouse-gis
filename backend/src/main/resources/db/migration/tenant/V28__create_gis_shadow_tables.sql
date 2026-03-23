CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE gis_zones (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    layout_block_id  UUID NOT NULL REFERENCES layout_blocks(id) ON DELETE CASCADE,
    name             VARCHAR(200),
    zone_type        VARCHAR(100),
    position_path    TEXT,
    geometry         GEOMETRY(Polygon, 4326) NOT NULL,
    centroid_geom    GEOMETRY(Point, 4326),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_gis_zones_geom ON gis_zones USING GIST (geometry);
CREATE INDEX idx_gis_zones_block ON gis_zones (layout_block_id);

CREATE TABLE gis_aisles (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    layout_block_id  UUID NOT NULL REFERENCES layout_blocks(id) ON DELETE CASCADE,
    name             VARCHAR(200),
    position_path    TEXT,
    geometry         GEOMETRY(Polygon, 4326) NOT NULL,
    centroid_geom    GEOMETRY(Point, 4326),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_gis_aisles_geom ON gis_aisles USING GIST (geometry);
CREATE INDEX idx_gis_aisles_block ON gis_aisles (layout_block_id);

CREATE TABLE gis_shelves (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    layout_block_id  UUID NOT NULL REFERENCES layout_blocks(id) ON DELETE CASCADE,
    location_code    VARCHAR(200),
    position_path    TEXT,
    geometry         GEOMETRY(Polygon, 4326) NOT NULL,
    centroid_geom    GEOMETRY(Point, 4326),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_gis_shelves_geom ON gis_shelves USING GIST (geometry);
CREATE INDEX idx_gis_shelves_block ON gis_shelves (layout_block_id);
