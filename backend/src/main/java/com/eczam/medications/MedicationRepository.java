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

    // Native SQL: filtering on the top-level category requires indexing into the
    // category_path JSONB array (category_path->>0), which JPQL cannot express.
    // Bind parameters are explicitly CAST(... AS text) for the same reason as the
    // JPQL version this replaced — without it, Postgres can't infer a type for a
    // NULL :q/:category bind used inside CONCAT()/comparison, and (at least on this
    // driver/version) resolves LOWER(...) to its bytea overload instead of text,
    // failing every call made with a blank/absent search query with
    // "function lower(bytea) does not exist".
    @Query(value = """
           SELECT m.* FROM medications m
           WHERE (CAST(:q AS text) IS NULL
                  OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:q AS text), '%'))
                  OR LOWER(m.generic_name) LIKE LOWER(CONCAT('%', CAST(:q AS text), '%')))
             AND (CAST(:category AS text) IS NULL OR m.category_path->>0 = CAST(:category AS text))
           ORDER BY m.name ASC
           """,
           countQuery = """
           SELECT count(*) FROM medications m
           WHERE (CAST(:q AS text) IS NULL
                  OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:q AS text), '%'))
                  OR LOWER(m.generic_name) LIKE LOWER(CONCAT('%', CAST(:q AS text), '%')))
             AND (CAST(:category AS text) IS NULL OR m.category_path->>0 = CAST(:category AS text))
           """,
           nativeQuery = true)
    Page<Medication> search(@Param("q") String q, @Param("category") String category, Pageable pageable);

    /** Real-leaflet rows still awaiting embedding — drives the Stage B seed (resumable). */
    @Query("SELECT m.id FROM Medication m WHERE m.leafletRaw IS NOT NULL AND m.vectorIndexed = false ORDER BY m.id")
    List<UUID> findUnindexedLeafletIds();

    /** Interface projection for the native GROUP BY below (col aliases `category`/`cnt` map to the getters). */
    interface CategoryCount {
        String getCategory();
        long getCnt();
    }

    // Distinct top-level categories (first element of category_path) with medication counts,
    // most-common first. Rows with no category_path (or an empty one) are excluded — they have
    // no top-level category to report and must never be attributed to any bucket.
    @Query(value = """
           SELECT category_path->>0 AS category, count(*) AS cnt
           FROM medications
           WHERE category_path IS NOT NULL AND jsonb_array_length(category_path) > 0
           GROUP BY category_path->>0
           ORDER BY cnt DESC
           """, nativeQuery = true)
    List<CategoryCount> findCategoryCounts();
}
