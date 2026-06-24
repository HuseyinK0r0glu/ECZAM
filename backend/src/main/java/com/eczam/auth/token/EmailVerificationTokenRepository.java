package com.eczam.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /** Invalidate any previous unused tokens for the user before issuing a new one. */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.user.id = :userId AND t.usedAt IS NULL")
    void deleteUnusedForUser(@Param("userId") UUID userId);

    /** Housekeeping: remove tokens expired before cutoff. */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
