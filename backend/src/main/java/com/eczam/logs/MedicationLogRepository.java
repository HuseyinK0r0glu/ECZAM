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

    // CAST(:from/:to AS timestamp) forces an explicit type on each bind parameter —
    // without it, a null :from/:to (the common case: history() called with no date
    // range) leaves Postgres unable to infer the parameter's type and it fails every
    // such call with "could not determine data type of parameter $n" (SQLState 42P18).
    @Query("""
           SELECT l FROM MedicationLog l
           WHERE l.userMedicationId = :umId
             AND (CAST(:from AS timestamp) IS NULL OR l.takenAt >= :from)
             AND (CAST(:to AS timestamp) IS NULL OR l.takenAt <= :to)
           ORDER BY l.takenAt DESC
           """)
    Page<MedicationLog> history(@Param("umId") UUID umId,
                                @Param("from") OffsetDateTime from,
                                @Param("to") OffsetDateTime to,
                                Pageable pageable);

    // Cross-medication export (dose-history-export feature): all logs for a set of
    // user_medications ids (i.e. every box the caller owns), scoped by date range.
    // Ownership is enforced by the service, which only passes umIds it already
    // resolved from UserMedication rows owned by the caller.
    @Query("""
           SELECT l FROM MedicationLog l
           WHERE l.userMedicationId IN :userMedicationIds
             AND l.takenAt >= :from AND l.takenAt <= :to
           ORDER BY l.takenAt DESC
           """)
    List<MedicationLog> findAllForUserMedicationIds(@Param("userMedicationIds") List<UUID> userMedicationIds,
                                                     @Param("from") OffsetDateTime from,
                                                     @Param("to") OffsetDateTime to);

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
