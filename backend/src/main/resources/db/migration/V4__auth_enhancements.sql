-- ============================================================
-- V4: Production auth enhancements
--   • Users: email_verified, lockout, Google OAuth, soft-delete, role
--   • Refresh tokens (rotation + compromise detection)
--   • Email verification tokens
--   • Audit log
-- ============================================================

-- Extend users table
ALTER TABLE users
    ADD COLUMN email_verified         BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN failed_login_attempts  INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN locked_until           TIMESTAMPTZ,
    ADD COLUMN google_sub             VARCHAR(255),
    ADD COLUMN role                   VARCHAR(20)  NOT NULL DEFAULT 'USER',
    ADD COLUMN deleted_at             TIMESTAMPTZ;

-- Partial unique index: google_sub uniqueness only when set
CREATE UNIQUE INDEX idx_users_google_sub
    ON users(google_sub)
    WHERE google_sub IS NOT NULL;

-- Soft-delete filtering index
CREATE INDEX idx_users_deleted_at ON users(deleted_at) WHERE deleted_at IS NOT NULL;

-- ============================================================
-- Refresh tokens (opaque, rotation-based)
--   Each row = one issued refresh token.
--   family groups all tokens in a rotation chain.
--   Reuse of a revoked token => entire family revoked.
-- ============================================================
CREATE TABLE refresh_tokens (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   TEXT         NOT NULL UNIQUE,   -- SHA-256(raw token) in hex
    family       UUID         NOT NULL,           -- rotation chain id
    revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at   TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ  NOT NULL,
    user_agent   TEXT,
    ip_address   VARCHAR(45),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family    ON refresh_tokens(family);
-- sparse index used by cleanup task
CREATE INDEX idx_refresh_tokens_expires   ON refresh_tokens(expires_at)
    WHERE revoked = FALSE;

-- ============================================================
-- Email verification tokens
-- ============================================================
CREATE TABLE email_verification_tokens (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   TEXT         NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ  NOT NULL,
    used_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_verify_user ON email_verification_tokens(user_id);

-- ============================================================
-- Audit log (append-only, KVKK SEC-K09)
-- ============================================================
CREATE TABLE audit_logs (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      UUID         REFERENCES users(id) ON DELETE SET NULL,
    event_type   VARCHAR(50)  NOT NULL,
    details      JSONB,
    ip_address   VARCHAR(45),
    user_agent   TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_user_id    ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_event_type ON audit_logs(event_type);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
