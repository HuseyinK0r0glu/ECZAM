-- ============================================================
-- V7: Typo-tolerant catalog search (pg_trgm)
-- ============================================================
-- The catalog search endpoint (GET /medications?q=) used a plain
-- LOWER(name) LIKE LOWER('%q%') substring scan: no typo tolerance, no
-- relevance ranking, and not indexable by a normal B-tree. pg_trgm is a
-- standard Postgres contrib extension (always available on the
-- pgvector/pgvector:pg16 image, which is stock Postgres 16 + pgvector) that
-- adds trigram-based similarity() / word_similarity() functions and the
-- %, <%, %> operators, all of which these GIN indexes accelerate.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_medications_name_trgm
    ON medications USING gin (name gin_trgm_ops);

CREATE INDEX idx_medications_generic_name_trgm
    ON medications USING gin (generic_name gin_trgm_ops);
