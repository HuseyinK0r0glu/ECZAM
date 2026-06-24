package com.eczam.auth.totp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TotpPendingRepository extends JpaRepository<TotpPendingEnrollment, UUID> {

    Optional<TotpPendingEnrollment> findByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM TotpPendingEnrollment t WHERE t.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
