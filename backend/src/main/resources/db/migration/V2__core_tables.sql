CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   TEXT NOT NULL,
    display_name    VARCHAR(100),
    notification_preferences JSONB NOT NULL DEFAULT '{
        "push": true, "email": false,
        "low_stock_threshold": 7, "expiry_warning_days": 30
    }',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE medications (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(255) NOT NULL,
    generic_name       VARCHAR(255),
    manufacturer       VARCHAR(255),
    barcode            VARCHAR(100) UNIQUE,
    -- Canonical 14-digit GTIN: the join key for GS1 DataMatrix scans (AI 01).
    -- Raw `barcode` is mostly EAN-13; a scan yields GTIN-14, so both sides are
    -- normalised to 14 digits before lookup. NULL for unparseable barcodes
    -- (search-only, never scan-matchable). UNIQUE allows many NULLs by default.
    gtin               VARCHAR(14) UNIQUE,
    atc_code           VARCHAR(16),
    atc_group          VARCHAR(1),               -- ATC anatomical main group (first letter)
    active_ingredient  VARCHAR(512),            -- NULL when source value is a placeholder
    category_path      JSONB,                   -- cleaned ordered therapeutic-category array
    form               VARCHAR(50),
    strength           VARCHAR(50),
    leaflet_raw        TEXT,                    -- full leaflet text, only when real
    leaflet_sections   JSONB,
    leaflet_truncated  BOOLEAN NOT NULL DEFAULT FALSE,  -- source text hit the ~32k ceiling
    leaflet_hash       VARCHAR(64),              -- SHA-256 of leaflet_raw; skip re-embed when unchanged
    vector_indexed     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_medications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    medication_id   UUID NOT NULL REFERENCES medications(id),
    quantity        NUMERIC(10, 2) NOT NULL DEFAULT 0,
    unit            VARCHAR(20) NOT NULL DEFAULT 'pill',
    -- Per-physical-box GS1 facts, decoded fresh on every scan and NEVER derived
    -- from the medication catalog. One row = one physical box a user owns.
    batch           VARCHAR(64),    -- GS1 AI 10 (lot)
    serial_number   VARCHAR(64),    -- GS1 AI 21
    expiration_date DATE,           -- GS1 AI 17
    notes           TEXT,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Each physical box is its own row (two boxes may share a product + expiry
    -- but differ by batch/serial). NULLs are distinct in Postgres unique keys,
    -- so manually-added boxes without batch/serial are not over-merged.
    UNIQUE (user_id, medication_id, batch, serial_number, expiration_date)
);

CREATE TABLE medication_schedules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_medication_id  UUID NOT NULL REFERENCES user_medications(id) ON DELETE CASCADE,
    dosage_amount       NUMERIC(6, 2) NOT NULL,
    frequency_type      VARCHAR(20) NOT NULL,
    frequency_value     INTEGER,
    scheduled_times     TIME[] NOT NULL,
    days_of_week        SMALLINT[],
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    starts_on           DATE NOT NULL DEFAULT CURRENT_DATE,
    ends_on             DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE medication_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_medication_id  UUID NOT NULL REFERENCES user_medications(id) ON DELETE CASCADE,
    schedule_id         UUID REFERENCES medication_schedules(id) ON DELETE SET NULL,
    taken_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    quantity_used       NUMERIC(6, 2) NOT NULL,
    notes               TEXT,
    -- Idempotency key supplied by the (offline-capable) client. Replaying the
    -- same dose-log POST with the same key is a no-op, so a queued offline write
    -- retried on reconnect never double-decrements stock. Unique per box
    -- (see V3 partial index). NULL = legacy/no-key writes (always insert).
    client_request_id   VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE push_subscriptions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint    TEXT NOT NULL UNIQUE,
    p256dh      TEXT NOT NULL,
    auth        TEXT NOT NULL,
    user_agent  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE leaflet_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    medication_id   UUID NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    section_name    VARCHAR(100) NOT NULL,
    section_ordinal SMALLINT,            -- 1–5 canonical KT section, for ordered citation
    chunk_text      TEXT NOT NULL,
    char_start      INTEGER,             -- provenance back into leaflet_raw
    char_len        INTEGER,
    token_count     SMALLINT,            -- approx, for retrieval budgeting
    source_lang     CHAR(2) NOT NULL DEFAULT 'tr',
    embedding       VECTOR(1536),
    chunk_index     INTEGER NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
