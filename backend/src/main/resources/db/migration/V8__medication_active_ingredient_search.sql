-- ============================================================
-- V8: extend catalog search's trigram coverage to active_ingredient
-- ============================================================
-- V7 added pg_trgm + GIN trigram indexes for name/generic_name. Reconciling
-- two independently-built search branches folded a third search column,
-- active_ingredient, into the same word_similarity() ranking
-- (MedicationSearchRepository.searchBySimilarity) — this index accelerates
-- trigram operators against it the same way V7 already does for the other
-- two columns. pg_trgm itself is already enabled by V7.

CREATE INDEX idx_medications_active_ingredient_trgm
    ON medications USING gin (active_ingredient gin_trgm_ops)
    WHERE active_ingredient IS NOT NULL;
