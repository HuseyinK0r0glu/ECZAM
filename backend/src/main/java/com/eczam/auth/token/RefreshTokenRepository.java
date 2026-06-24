package com.eczam.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedFalseOrderByCreatedAtDesc(UUID userId);

    /** Revoke all tokens in a family — used when token reuse (compromise) is detected. */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = CURRENT_TIMESTAMP " +
           "WHERE r.family = :family AND r.revoked = false")
    void revokeFamily(@Param("family") UUID family);

    /** Revoke all active tokens for a user (logout from all devices). */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = CURRENT_TIMESTAMP " +
           "WHERE r.user.id = :userId AND r.revoked = false")
    void revokeAllForUser(@Param("userId") UUID userId);

    /** Revoke a specific token by ID (logout single device). */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = CURRENT_TIMESTAMP " +
           "WHERE r.id = :id AND r.revoked = false")
    void revokeById(@Param("id") UUID id);

    /** Housekeeping: delete tokens expired before the given cutoff. */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);

    long countByUserIdAndRevokedFalseAndExpiresAtAfter(UUID userId, OffsetDateTime now);
}
