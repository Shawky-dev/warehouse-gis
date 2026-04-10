-- V40: Remove geoserver_layer column from gis_data_layers.
-- Data layers are now stored as plain images (PNG/JPEG) and served directly,
-- replacing the previous GeoTIFF + GeoServer WMS approach.

ALTER TABLE gis_data_layers DROP COLUMN IF EXISTS geoserver_layer;
