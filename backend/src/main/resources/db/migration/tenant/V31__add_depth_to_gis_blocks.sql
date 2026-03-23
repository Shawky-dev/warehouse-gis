ALTER TABLE gis_blocks ADD COLUMN depth INTEGER NOT NULL DEFAULT 0;
CREATE INDEX idx_gis_blocks_depth ON gis_blocks (depth);
