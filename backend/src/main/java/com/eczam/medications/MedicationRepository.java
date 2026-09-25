package com.eczam.medications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {
    Optional<Medication> findByBarcode(String barcode);
    Optional<Medication> findByGtin(String gtin);

    /**
     * Fuzzy, typo-tolerant, relevance-ranked catalog search (pg_trgm; see
     * V7__pg_trgm_search.sql). JPQL can't call pg_trgm functions, so this is a
     * native query; {@code countQuery} is required for Spring Data to paginate
     * a native query.
     *
     * <p>{@code q IS NULL} preserves the pre-existing "browse everything"
     * behavior (MedicationService.search() maps a blank q to null) — full
     * catalog, ordered by name, untouched by any of the ranking below.
     *
     * <p>Matching: a row matches if {@code q} is a substring of name /
     * generic_name / active_ingredient (case-insensitive, ILIKE) OR its
     * {@code word_similarity(q, column)} clears 0.3 on any of those columns.
     * {@code word_similarity} (rather than plain {@code similarity}) scores q
     * against the best-matching substring of the column instead of the whole
     * string, which matters here because {@code name} is usually
     * "DRUG dose form" (e.g. "IBUPROFEN 400 MG TABLET") — comparing a bare
     * drug-name query against that whole string with plain {@code similarity}
     * dilutes the score with the unrelated " 400 MG TABLET" tail. 0.3 is
     * pg_trgm's own conventional similarity threshold (the default
     * `pg_trgm.similarity_threshold` GUC); empirically (see the manual checks
     * run against realistic Turkish drug names during development) common
     * single-letter transpositions/omissions/insertions on a fixture like
     * "IBUPROFEN 400 MG TABLET" score 0.33–0.82 against that threshold, while
     * unrelated queries score 0 — i.e. 0.3 cleanly separates "typo of a real
     * name" from "not this drug" with comfortable margin on both sides.
     *
     * <p>Ranking (best first): (1) name starts with q, (2) q is a substring of
     * name/generic_name/active_ingredient anywhere, (3) fuzzy-only trigram
     * matches — ranked within each tier by the best word_similarity score
     * across the three columns, descending, then by name ascending as the
     * final, stable tiebreaker. Exact/prefix/substring hits — the
     * unambiguously "correct" matches — always outrank a fuzzy guess,
     * regardless of trigram score.
     */
    @Query(value = """
           SELECT m.* FROM medications m
           WHERE :q IS NULL
              OR m.name ILIKE CONCAT('%', :q, '%')
              OR m.generic_name ILIKE CONCAT('%', :q, '%')
              OR m.active_ingredient ILIKE CONCAT('%', :q, '%')
              OR word_similarity(:q, m.name) > 0.3
              OR word_similarity(:q, COALESCE(m.generic_name, '')) > 0.3
              OR word_similarity(:q, COALESCE(m.active_ingredient, '')) > 0.3
           ORDER BY
              CASE
                WHEN :q IS NULL THEN 0
                WHEN m.name ILIKE CONCAT(:q, '%') THEN 0
                WHEN m.name ILIKE CONCAT('%', :q, '%')
                  OR m.generic_name ILIKE CONCAT('%', :q, '%')
                  OR m.active_ingredient ILIKE CONCAT('%', :q, '%') THEN 1
                ELSE 2
              END ASC,
              CASE WHEN :q IS NULL THEN 0 ELSE
                GREATEST(
                  word_similarity(:q, m.name),
                  word_similarity(:q, COALESCE(m.generic_name, '')),
                  word_similarity(:q, COALESCE(m.active_ingredient, ''))
                )
              END DESC,
              m.name ASC
           """,
           countQuery = """
           SELECT count(*) FROM medications m
           WHERE :q IS NULL
              OR m.name ILIKE CONCAT('%', :q, '%')
              OR m.generic_name ILIKE CONCAT('%', :q, '%')
              OR m.active_ingredient ILIKE CONCAT('%', :q, '%')
              OR word_similarity(:q, m.name) > 0.3
              OR word_similarity(:q, COALESCE(m.generic_name, '')) > 0.3
              OR word_similarity(:q, COALESCE(m.active_ingredient, '')) > 0.3
           """,
           nativeQuery = true)
    Page<Medication> search(@Param("q") String q, Pageable pageable);

    /** Real-leaflet rows still awaiting embedding — drives the Stage B seed (resumable). */
    @Query("SELECT m.id FROM Medication m WHERE m.leafletRaw IS NOT NULL AND m.vectorIndexed = false ORDER BY m.id")
    List<UUID> findUnindexedLeafletIds();
}
