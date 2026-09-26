package com.eczam.logs;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
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
}
