CREATE TABLE IF NOT EXISTS units_of_measure (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    symbol VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_units_of_measure_code_ci
    ON units_of_measure ((LOWER(code)));

CREATE INDEX IF NOT EXISTS idx_units_of_measure_active_name
    ON units_of_measure (active, name);

CREATE TABLE IF NOT EXISTS suppliers (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    contact_name VARCHAR(160),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(60),
    notes VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_suppliers_code_ci
    ON suppliers ((LOWER(code)));

CREATE INDEX IF NOT EXISTS idx_suppliers_active_name
    ON suppliers (active, name);

CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY,
    sku VARCHAR(60) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    base_uom_id UUID NOT NULL REFERENCES units_of_measure(id),
    track_lot BOOLEAN NOT NULL DEFAULT FALSE,
    track_expiry BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_products_sku_ci
    ON products ((LOWER(sku)));

CREATE INDEX IF NOT EXISTS idx_products_active_name
    ON products (active, name);

CREATE INDEX IF NOT EXISTS idx_products_base_uom
    ON products (base_uom_id);

CREATE TABLE IF NOT EXISTS product_suppliers (
    product_id UUID NOT NULL REFERENCES products(id),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (product_id, supplier_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_suppliers_single_primary
    ON product_suppliers (product_id)
    WHERE is_primary = TRUE;

CREATE INDEX IF NOT EXISTS idx_product_suppliers_supplier
    ON product_suppliers (supplier_id);

CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_email VARCHAR(255) NOT NULL,
    actor_roles JSONB NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(80) NOT NULL,
    before_state JSONB,
    after_state JSONB,
    tenant_id VARCHAR(30) NOT NULL,
    request_path VARCHAR(500),
    request_method VARCHAR(20)
);

CREATE INDEX IF NOT EXISTS idx_audit_log_occurred_at
    ON audit_log (occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_entity
    ON audit_log (entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_audit_log_actor_email
    ON audit_log (actor_email);
