ALTER TABLE layout_blocks
  ADD COLUMN location_kind VARCHAR(20) NOT NULL DEFAULT 'STORAGE',
  ADD COLUMN scan_code     VARCHAR(60),
  ADD COLUMN full_code     VARCHAR(200);

CREATE UNIQUE INDEX uq_layout_blocks_scan_code
  ON layout_blocks(scan_code)
  WHERE scan_code IS NOT NULL;
