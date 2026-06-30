-- Therapeutic-class queries on the catalog. (gtin lookups use the UNIQUE
-- constraint's implicit index; that exact-match path is the scan hot path.)
CREATE INDEX idx_medications_atc ON medications(atc_code) WHERE atc_code IS NOT NULL;

CREATE INDEX idx_user_medications_user_id ON user_medications(user_id);
CREATE INDEX idx_user_medications_expiration ON user_medications(expiration_date)
    WHERE expiration_date IS NOT NULL;
CREATE INDEX idx_medication_schedules_active ON medication_schedules(user_medication_id)
    WHERE active = TRUE;
CREATE INDEX idx_medication_logs_user_med ON medication_logs(user_medication_id, taken_at DESC);
-- Idempotency: one log per (box, client key). Partial so legacy NULL-key writes
-- don't collide. Lets a retried offline dose-log POST resolve to the existing row.
CREATE UNIQUE INDEX idx_medication_logs_idem
    ON medication_logs(user_medication_id, client_request_id)
    WHERE client_request_id IS NOT NULL;
CREATE INDEX idx_push_subscriptions_user_id ON push_subscriptions(user_id);
CREATE INDEX idx_leaflet_chunks_medication ON leaflet_chunks(medication_id);
CREATE INDEX idx_leaflet_chunks_embedding ON leaflet_chunks
    USING hnsw (embedding vector_cosine_ops);
