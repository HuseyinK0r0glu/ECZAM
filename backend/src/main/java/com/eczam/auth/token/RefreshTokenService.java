package com.eczam.auth.token;

import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import com.eczam.users.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Manages opaque refresh tokens with family-based rotation.
 *
 * Security properties:
 *  - Tokens are random 256-bit values (never JWT); stored as SHA-256 hash.
 *  - On every /auth/refresh: old token is revoked, new token issued in same family.
 *  - If a revoked token is presented → entire family revoked (compromise signal).
 *  - Logout revokes the single presented token; logout-all revokes every token.
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits
    private static final SecureRandom RNG = new SecureRandom();

    private final RefreshTokenRepository repo;
    private final long refreshTtlSeconds;

    public RefreshTokenService(RefreshTokenRepository repo,
                               @Value("${eczam.jwt.refresh-ttl:P7D}") String refreshTtl) {
        this.repo = repo;
        // parse ISO-8601 duration like P7D → seconds
        this.refreshTtlSeconds = java.time.Duration.parse(refreshTtl).toSeconds();
    }

    // -------------------------------------------------------------------------
    // Issue
    // -------------------------------------------------------------------------

    /** Issue a brand-new token in a new family (first login / register). */
    @Transactional
    public String issue(User user, HttpServletRequest request) {
        return createToken(user, UUID.randomUUID(), request);
    }

    // -------------------------------------------------------------------------
    // Rotate
    // -------------------------------------------------------------------------

    /**
     * Validates the presented raw token, then:
     *  - Revokes the old token.
     *  - Detects reuse of an already-revoked token and revokes the whole family.
     *  - Issues a new token in the same family.
     *
     * @return new raw token string
     * @throws ApiException (401) if the token is invalid / expired / family compromised
     */
    @Transactional
    public RotationResult rotate(String rawToken, HttpServletRequest request) {
        String hash = sha256(rawToken);
        RefreshToken stored = repo.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));

        if (stored.isRevoked()) {
            // Reuse of a revoked token — assume compromise: revoke entire family.
            log.warn("Refresh token reuse detected for family={}, userId={}",
                    stored.getFamily(), stored.getUser().getId());
            repo.revokeFamily(stored.getFamily());
            throw ApiException.unauthorized("Refresh token already used — all sessions revoked for security");
        }

        if (stored.isExpired()) {
            throw ApiException.unauthorized("Refresh token has expired");
        }

        // Revoke old token
        stored.setRevoked(true);
        stored.setRevokedAt(OffsetDateTime.now());
        repo.save(stored);

        // Issue new token in same family
        String newRaw = createToken(stored.getUser(), stored.getFamily(), request);
        return new RotationResult(newRaw, stored.getUser());
    }

    // -------------------------------------------------------------------------
    // Revocation
    // -------------------------------------------------------------------------

    /** Revoke a single token (single-device logout). */
    @Transactional
    public void revoke(String rawToken) {
        String hash = sha256(rawToken);
        repo.findByTokenHash(hash).ifPresent(t -> {
            t.setRevoked(true);
            t.setRevokedAt(OffsetDateTime.now());
            repo.save(t);
        });
    }

    /** Revoke all active tokens for a user (logout from all devices). */
    @Transactional
    public void revokeAll(UUID userId) {
        repo.revokeAllForUser(userId);
    }

    // -------------------------------------------------------------------------
    // Session listing (for /auth/sessions)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RefreshToken> activeSessions(UUID userId) {
        return repo.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .filter(t -> !t.isExpired())
                .toList();
    }

    /** Revoke a specific session by its token ID. */
    @Transactional
    public void revokeSession(UUID tokenId, UUID userId) {
        RefreshToken t = repo.findById(tokenId)
                .filter(rt -> rt.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound(ErrorCode.NOT_FOUND, "Session not found"));
        t.setRevoked(true);
        t.setRevokedAt(OffsetDateTime.now());
        repo.save(t);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String createToken(User user, UUID family, HttpServletRequest request) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RNG.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(sha256(raw));
        token.setFamily(family);
        token.setExpiresAt(OffsetDateTime.now().plusSeconds(refreshTtlSeconds));
        if (request != null) {
            token.setUserAgent(truncate(request.getHeader("User-Agent"), 250));
            token.setIpAddress(extractIp(request));
        }
        repo.save(token);
        return raw;
    }

    public static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String extractIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    public record RotationResult(String rawToken, User user) {}
}
