-- Fuzzy, typo-tolerant catalog search (real-world 20k-row Turkish medicine
-- import has plenty of near-duplicate/typo-prone names) via pg_trgm trigram
-- similarity, layered on top of the existing ILIKE substring match.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- GIN trigram indexes accelerate the ILIKE '%q%' substring disjunct in
-- MedicationRepository.search() (pg_trgm makes GIN indexes usable for
-- LIKE/ILIKE, not just the similarity operators) and are the standard,
-- idiomatic index to carry alongside trigram-similarity search on these
-- columns so a future move from the word_similarity() function form to the
-- indexable `<%` operator needs no further migration.
CREATE INDEX idx_medications_name_trgm
    ON medications USING gin (name gin_trgm_ops);

CREATE INDEX idx_medications_generic_name_trgm
    ON medications USING gin (generic_name gin_trgm_ops)
    WHERE generic_name IS NOT NULL;

CREATE INDEX idx_medications_active_ingredient_trgm
    ON medications USING gin (active_ingredient gin_trgm_ops)
    WHERE active_ingredient IS NOT NULL;
