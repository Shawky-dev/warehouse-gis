CREATE SCHEMA IF NOT EXISTS master;
CREATE TABLE IF NOT EXISTS master.users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    role VARCHAR(50) NOT NULL DEFAULT 'ADMIN'
);

CREATE TABLE IF NOT EXISTS master.auth_refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES master.users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    token_family_id UUID NOT NULL,
    parent_token_id UUID NULL REFERENCES master.auth_refresh_tokens(id) ON DELETE SET NULL,
    replaced_by_token_id UUID NULL REFERENCES master.auth_refresh_tokens(id) ON DELETE SET NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NULL,
    rotated_at TIMESTAMPTZ NULL,
    revoked_at TIMESTAMPTZ NULL,
    revocation_reason VARCHAR(100) NULL,
    reuse_detected_at TIMESTAMPTZ NULL,
    created_by_ip VARCHAR(64) NULL,
    created_by_user_agent VARCHAR(512) NULL,
    CONSTRAINT chk_auth_refresh_tokens_exp_gt_iat CHECK (expires_at > issued_at)
);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_user_id
    ON master.auth_refresh_tokens(user_id);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_family_id
    ON master.auth_refresh_tokens(token_family_id);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_expires_at
    ON master.auth_refresh_tokens(expires_at);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_active_lookup
    ON master.auth_refresh_tokens(token_hash, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE IF NOT EXISTS master.tenant (
    tenant_id VARCHAR(30) PRIMARY KEY,
    schema VARCHAR(30) NOT NULL UNIQUE
);
