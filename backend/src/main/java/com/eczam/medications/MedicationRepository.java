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
}
