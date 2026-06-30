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
}
