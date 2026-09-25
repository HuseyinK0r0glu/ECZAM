package com.eczam.logs;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicationLogRepository extends JpaRepository<MedicationLog, UUID> {

    Optional<MedicationLog> findByUserMedicationIdAndClientRequestId(UUID userMedicationId, String clientRequestId);

    @Query("""
           SELECT l FROM MedicationLog l
           WHERE l.userMedicationId = :umId
             AND (:from IS NULL OR l.takenAt >= :from)
             AND (:to IS NULL OR l.takenAt <= :to)
           ORDER BY l.takenAt DESC
           """)
    Page<MedicationLog> history(@Param("umId") UUID umId,
                                @Param("from") OffsetDateTime from,
                                @Param("to") OffsetDateTime to,
                                Pageable pageable);

    /**
     * A user's dose logs in an instant range, across all their inventory items.
     * {@code MedicationLog} only stores the {@code user_medications.id} it was
     * logged against (no direct JPA association), so ownership is joined via a
     * subquery on {@code UserMedication.userId} — used by {@link AdherenceService}
     * to compute streaks over the full, never-purged log history.
     */
    @Query("""
           SELECT l FROM MedicationLog l
           WHERE l.userMedicationId IN (SELECT um.id FROM UserMedication um WHERE um.userId = :userId)
             AND l.takenAt >= :from
             AND l.takenAt <= :to
           """)
    List<MedicationLog> findForUserInRange(@Param("userId") UUID userId,
                                           @Param("from") OffsetDateTime from,
                                           @Param("to") OffsetDateTime to);
}
