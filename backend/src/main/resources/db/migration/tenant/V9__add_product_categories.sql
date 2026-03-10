CREATE TABLE IF NOT EXISTS product_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_categories_name_ci
    ON product_categories ((LOWER(name)));

CREATE INDEX IF NOT EXISTS idx_product_categories_active_name
    ON product_categories (active, name);

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS category_id UUID NULL REFERENCES product_categories(id);

CREATE INDEX IF NOT EXISTS idx_products_category
    ON products (category_id);
