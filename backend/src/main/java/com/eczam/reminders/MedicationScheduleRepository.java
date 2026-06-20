package com.eczam.reminders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, UUID> {

    @Query("SELECT s FROM MedicationSchedule s WHERE s.userMedication.userId = :userId ORDER BY s.createdAt DESC")
    List<MedicationSchedule> findAllForUser(@Param("userId") UUID userId);

    @Query("SELECT s FROM MedicationSchedule s WHERE s.userMedication.id = :umId AND s.userMedication.userId = :userId")
    List<MedicationSchedule> findForUserMedication(@Param("umId") UUID umId, @Param("userId") UUID userId);

    @Query("SELECT s FROM MedicationSchedule s WHERE s.id = :id AND s.userMedication.userId = :userId")
    Optional<MedicationSchedule> findByIdForUser(@Param("id") UUID id, @Param("userId") UUID userId);

    // Used by the Phase 4 scheduler:
    @Query("SELECT s FROM MedicationSchedule s WHERE s.active = true")
    List<MedicationSchedule> findAllActive();
}
