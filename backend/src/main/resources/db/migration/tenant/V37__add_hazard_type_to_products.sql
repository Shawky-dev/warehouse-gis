-- V37: Add hazard_type_id to products and backfill to NONE.

-- Step 1: add nullable column.
ALTER TABLE products
    ADD COLUMN hazard_type_id UUID NULL REFERENCES hazard_types(id);

-- Step 2: backfill all existing products to NONE hazard type.
UPDATE products
   SET hazard_type_id = (SELECT id FROM hazard_types WHERE code = 'NONE')
 WHERE hazard_type_id IS NULL;

-- Step 3: backfill any product that still has no category to STANDARD.
UPDATE products
   SET category_id = (SELECT id FROM product_categories WHERE code = 'STANDARD')
 WHERE category_id IS NULL;

-- Step 4: enforce NOT NULL.
ALTER TABLE products
    ALTER COLUMN hazard_type_id SET NOT NULL,
    ALTER COLUMN category_id    SET NOT NULL;

CREATE INDEX idx_products_hazard_type
    ON products (hazard_type_id);
