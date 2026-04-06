-- V35: Create hazard_types and zone_types lookup tables.
--      Seed default rows for both.

-- ── 1. hazard_types ───────────────────────────────────────────────────────────

CREATE TABLE hazard_types (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(60)  NOT NULL,
    display_name   VARCHAR(120) NOT NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMPTZ  NULL
);

CREATE UNIQUE INDEX uq_hazard_types_code
    ON hazard_types (code);

CREATE INDEX idx_hazard_types_active
    ON hazard_types (is_active);

INSERT INTO hazard_types (id, code, display_name) VALUES
    (gen_random_uuid(), 'NONE',      'None'),
    (gen_random_uuid(), 'FLAMMABLE', 'Flammable'),
    (gen_random_uuid(), 'EXPLOSIVE', 'Explosive'),
    (gen_random_uuid(), 'CHEMICAL',  'Chemical');

-- ── 2. zone_types ─────────────────────────────────────────────────────────────

CREATE TABLE zone_types (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(60)  NOT NULL,
    display_name   VARCHAR(120) NOT NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMPTZ  NULL
);

CREATE UNIQUE INDEX uq_zone_types_code
    ON zone_types (code);

CREATE INDEX idx_zone_types_active
    ON zone_types (is_active);

INSERT INTO zone_types (id, code, display_name) VALUES
    (gen_random_uuid(), 'REFRIGERATED', 'Refrigerated'),
    (gen_random_uuid(), 'NEAR_GATE',    'Near Gate'),
    (gen_random_uuid(), 'GENERAL',      'General');
