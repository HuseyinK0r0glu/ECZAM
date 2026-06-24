-- ============================================================
-- V6: Two-factor authentication (TOTP RFC 6238)
-- ============================================================

ALTER TABLE users
    ADD COLUMN totp_secret         TEXT,          -- base32 TOTP secret (encrypted at rest recommended)
    ADD COLUMN totp_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN totp_backup_codes   TEXT[],        -- one-time backup codes (hashed)
    ADD COLUMN totp_enrolled_at    TIMESTAMPTZ;

-- Users currently setting up 2FA (not yet confirmed)
CREATE TABLE totp_pending_enrollment (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    secret       TEXT         NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
