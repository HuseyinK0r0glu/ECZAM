-- ============================================================
-- V5: Performance indexes for common query patterns
-- ============================================================

-- Partial index on locked accounts for fast lockout check
CREATE INDEX idx_users_locked_until
    ON users(locked_until)
    WHERE locked_until IS NOT NULL;

-- Email verification lookup by email (for admin queries)
CREATE INDEX idx_users_email_verified
    ON users(email_verified)
    WHERE email_verified = FALSE;

-- Faster refresh token lookup by user for session listing
-- (already have idx_refresh_tokens_user_id but add expires filter)
CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens(user_id, expires_at DESC)
    WHERE revoked = FALSE;

-- Medication logs: additional index for date-range queries
CREATE INDEX idx_medication_logs_taken_at
    ON medication_logs(taken_at DESC);

-- User medications: index for expiry monitoring queries
CREATE INDEX idx_user_medications_user_expiry
    ON user_medications(user_id, expiration_date)
    WHERE expiration_date IS NOT NULL;

-- Audit log: composite for common admin queries
CREATE INDEX idx_audit_logs_user_event
    ON audit_logs(user_id, event_type, created_at DESC)
    WHERE user_id IS NOT NULL;
