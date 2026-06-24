package com.eczam.auth.token;

import com.eczam.shared.web.ApiException;
import com.eczam.users.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository repo;
    @Mock HttpServletRequest request;

    RefreshTokenService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repo, "P7D");
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
    }

    // ---------------------------------------------------------------- sha256
    @Test
    void sha256_is_deterministic() {
        String hash1 = RefreshTokenService.sha256("hello");
        String hash2 = RefreshTokenService.sha256("hello");
        assertThat(hash1).isEqualTo(hash2).hasSize(64); // SHA-256 hex = 64 chars
    }

    @Test
    void sha256_different_inputs_produce_different_hashes() {
        assertThat(RefreshTokenService.sha256("a")).isNotEqualTo(RefreshTokenService.sha256("b"));
    }

    // ---------------------------------------------------------------- issue
    @Test
    void issue_saves_token_and_returns_raw_token() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String raw = service.issue(user, request);

        assertThat(raw).isNotBlank().hasSizeGreaterThanOrEqualTo(40);
        ArgumentCaptor<RefreshToken> cap = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(cap.capture());
        RefreshToken saved = cap.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.sha256(raw));
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getUser()).isEqualTo(user);
    }

    @Test
    void issue_generates_unique_tokens_each_call() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String t1 = service.issue(user, request);
        String t2 = service.issue(user, request);

        assertThat(t1).isNotEqualTo(t2);
    }

    // ---------------------------------------------------------------- rotate
    @Test
    void rotate_revokes_old_token_and_issues_new_one() {
        String oldRaw = "old-raw-token";
        String oldHash = RefreshTokenService.sha256(oldRaw);
        UUID family = UUID.randomUUID();

        RefreshToken stored = new RefreshToken();
        stored.setId(UUID.randomUUID());
        stored.setUser(user);
        stored.setTokenHash(oldHash);
        stored.setFamily(family);
        stored.setRevoked(false);
        stored.setExpiresAt(OffsetDateTime.now().plusDays(7));

        when(repo.findByTokenHash(oldHash)).thenReturn(Optional.of(stored));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.RotationResult result = service.rotate(oldRaw, request);

        assertThat(result.rawToken()).isNotEqualTo(oldRaw);
        assertThat(result.user()).isEqualTo(user);
        assertThat(stored.isRevoked()).isTrue();
        assertThat(stored.getRevokedAt()).isNotNull();
    }

    @Test
    void rotate_with_same_family_for_chained_tokens() {
        String rawToken = "some-raw-token";
        UUID family = UUID.randomUUID();

        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash(RefreshTokenService.sha256(rawToken));
        stored.setFamily(family);
        stored.setRevoked(false);
        stored.setExpiresAt(OffsetDateTime.now().plusDays(7));

        when(repo.findByTokenHash(RefreshTokenService.sha256(rawToken))).thenReturn(Optional.of(stored));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rotate(rawToken, request);

        // New token saved with same family
        ArgumentCaptor<RefreshToken> cap = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo, times(2)).save(cap.capture()); // first save=revoke old, second save=new token
        RefreshToken newToken = cap.getAllValues().stream()
                .filter(t -> !t.isRevoked())
                .findFirst().orElseThrow();
        assertThat(newToken.getFamily()).isEqualTo(family);
    }

    // ---------------------------------------------------------------- compromise detection
    @Test
    void rotate_with_revoked_token_triggers_family_revocation_and_throws_401() {
        String reusedRaw = "already-used-token";
        UUID family = UUID.randomUUID();

        RefreshToken revokedToken = new RefreshToken();
        revokedToken.setUser(user);
        revokedToken.setTokenHash(RefreshTokenService.sha256(reusedRaw));
        revokedToken.setFamily(family);
        revokedToken.setRevoked(true);
        revokedToken.setRevokedAt(OffsetDateTime.now().minusHours(1));
        revokedToken.setExpiresAt(OffsetDateTime.now().plusDays(7));

        when(repo.findByTokenHash(RefreshTokenService.sha256(reusedRaw)))
                .thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> service.rotate(reusedRaw, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(repo).revokeFamily(family);
    }

    @Test
    void rotate_with_unknown_token_throws_401() {
        when(repo.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("unknown-token", request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void rotate_with_expired_token_throws_401() {
        String raw = "expired-token";
        RefreshToken expired = new RefreshToken();
        expired.setUser(user);
        expired.setTokenHash(RefreshTokenService.sha256(raw));
        expired.setFamily(UUID.randomUUID());
        expired.setRevoked(false);
        expired.setExpiresAt(OffsetDateTime.now().minusSeconds(1));

        when(repo.findByTokenHash(RefreshTokenService.sha256(raw))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate(raw, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ---------------------------------------------------------------- revoke
    @Test
    void revoke_marks_token_as_revoked() {
        String raw = "token-to-revoke";
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(RefreshTokenService.sha256(raw));
        token.setFamily(UUID.randomUUID());
        token.setRevoked(false);
        token.setExpiresAt(OffsetDateTime.now().plusDays(7));

        when(repo.findByTokenHash(RefreshTokenService.sha256(raw))).thenReturn(Optional.of(token));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.revoke(raw);

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isNotNull();
        verify(repo).save(token);
    }

    @Test
    void revoke_unknown_token_does_nothing() {
        when(repo.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatNoException().isThrownBy(() -> service.revoke("unknown"));
        verify(repo, never()).save(any());
    }

    // ---------------------------------------------------------------- revokeAll
    @Test
    void revoke_all_delegates_to_repository() {
        UUID userId = user.getId();
        service.revokeAll(userId);
        verify(repo).revokeAllForUser(userId);
    }

    // ---------------------------------------------------------------- activeSessions
    @Test
    void active_sessions_excludes_expired_tokens() {
        RefreshToken active = new RefreshToken();
        active.setExpiresAt(OffsetDateTime.now().plusDays(1));
        active.setRevoked(false);

        RefreshToken expired = new RefreshToken();
        expired.setExpiresAt(OffsetDateTime.now().minusDays(1));
        expired.setRevoked(false);

        when(repo.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(active, expired));

        List<RefreshToken> result = service.activeSessions(user.getId());

        assertThat(result).containsExactly(active);
    }
}
