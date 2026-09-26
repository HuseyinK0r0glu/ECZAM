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

    // CAST(:q AS string) forces an explicit text type on the bind parameter — without
    // it, Postgres can't infer a type for a NULL :q used inside CONCAT(), and (at
    // least on this driver/version) resolves LOWER(...) to its bytea overload instead
    // of text, failing every call made with a blank/absent search query with
    // "function lower(bytea) does not exist".
    @Query("""
           SELECT m FROM Medication m
           WHERE CAST(:q AS string) IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
              OR LOWER(m.genericName) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
           ORDER BY m.name ASC
           """)
    Page<Medication> search(@Param("q") String q, Pageable pageable);

    /** Real-leaflet rows still awaiting embedding — drives the Stage B seed (resumable). */
    @Query("SELECT m.id FROM Medication m WHERE m.leafletRaw IS NOT NULL AND m.vectorIndexed = false ORDER BY m.id")
    List<UUID> findUnindexedLeafletIds();
}
