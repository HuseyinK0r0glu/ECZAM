package com.eczam.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserMedicationRepository extends JpaRepository<UserMedication, UUID> {
    List<UserMedication> findByUserIdOrderByAddedAtDesc(UUID userId);
    Optional<UserMedication> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndMedicationIdAndExpirationDate(UUID userId, UUID medicationId, java.time.LocalDate expirationDate);
    // Per-box guard: a GS1 serial (AI 21) is globally unique to one physical box,
    // so the same scanned box can't be added twice for a user.
    boolean existsByUserIdAndSerialNumber(UUID userId, String serialNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT um FROM UserMedication um WHERE um.id = :id AND um.userId = :userId")
    Optional<UserMedication> findByIdAndUserIdForUpdate(UUID id, UUID userId);

    // --- Scheduler triggers (Phase 4) ---

    @Query(value = """
            SELECT um.* FROM user_medications um
            JOIN users u ON u.id = um.user_id
            WHERE um.quantity <= CAST(u.notification_preferences->>'low_stock_threshold' AS numeric)
            """, nativeQuery = true)
    List<UserMedication> findLowStock();

    @Query(value = """
            SELECT um.* FROM user_medications um
            JOIN users u ON u.id = um.user_id
            WHERE um.expiration_date IS NOT NULL
              AND um.expiration_date BETWEEN CURRENT_DATE
                  AND CURRENT_DATE + CAST(u.notification_preferences->>'expiry_warning_days' AS integer)
            """, nativeQuery = true)
    List<UserMedication> findExpiringSoon();

    @Query(value = """
            SELECT * FROM user_medications WHERE expiration_date < CURRENT_DATE
            """, nativeQuery = true)
    List<UserMedication> findExpired();

    // Per-user variants for the Expiration page:
    @Query(value = """
            SELECT um.* FROM user_medications um
            JOIN users u ON u.id = um.user_id
            WHERE um.user_id = :userId AND um.expiration_date IS NOT NULL
              AND um.expiration_date BETWEEN CURRENT_DATE
                  AND CURRENT_DATE + COALESCE(:days, CAST(u.notification_preferences->>'expiry_warning_days' AS integer))
            ORDER BY um.expiration_date ASC
            """, nativeQuery = true)
    List<UserMedication> findExpiringSoonForUser(UUID userId, Integer days);

    @Query(value = """
            SELECT * FROM user_medications
            WHERE user_id = :userId AND expiration_date < CURRENT_DATE
            ORDER BY expiration_date ASC
            """, nativeQuery = true)
    List<UserMedication> findExpiredForUser(UUID userId);
}
