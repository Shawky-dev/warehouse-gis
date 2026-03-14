ALTER TABLE stock_movements
    ADD COLUMN source_document_id UUID,
    ADD COLUMN reason_code VARCHAR(50);

CREATE INDEX idx_stock_movements_source_document
    ON stock_movements(source_document_id)
    WHERE source_document_id IS NOT NULL;