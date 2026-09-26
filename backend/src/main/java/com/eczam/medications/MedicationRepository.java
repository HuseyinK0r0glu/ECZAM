package com.eczam.medications;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {
    Optional<Medication> findByBarcode(String barcode);
    Optional<Medication> findByGtin(String gtin);

    // Catalog free-text search (GET /medications?q=) lives in
    // MedicationSearchRepository — it needs keyset pagination on a computed
    // trigram-similarity score, which a derived/JPQL query can't express.

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
