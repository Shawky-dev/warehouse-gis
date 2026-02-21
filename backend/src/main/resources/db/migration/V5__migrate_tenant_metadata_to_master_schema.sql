CREATE SCHEMA IF NOT EXISTS master;

CREATE TABLE IF NOT EXISTS master.tenant (
    tenant_id VARCHAR(30) PRIMARY KEY,
    schema VARCHAR(30) NOT NULL
);

INSERT INTO master.tenant (tenant_id, schema)
SELECT
    t.tenant_id,
    COALESCE(to_jsonb(t) ->> 'schema', to_jsonb(t) ->> 'db')
FROM public.tenant t
ON CONFLICT (tenant_id) DO UPDATE SET schema = EXCLUDED.schema;

DROP TABLE IF EXISTS public.tenant;
