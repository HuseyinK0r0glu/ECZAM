package com.eczam.auth;

import com.eczam.auth.dto.AuthDtos.*;
import com.eczam.auth.email.EmailService;
import com.eczam.auth.oauth.GoogleOAuthService;
import com.eczam.auth.token.EmailVerificationTokenRepository;
import com.eczam.auth.token.RefreshTokenService;
import com.eczam.audit.AuditService;
import com.eczam.shared.security.JwtProperties;
import com.eczam.shared.security.JwtService;
import com.eczam.shared.security.PasswordPolicy;
import com.eczam.shared.web.ApiException;
import com.eczam.users.User;
import com.eczam.users.UserRepository;
import io.micrometer.core.instrument.Counter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private final UserRepository users               = mock(UserRepository.class);
    private final PasswordEncoder encoder            = new BCryptPasswordEncoder();
    private final RefreshTokenService refreshTokens  = mock(RefreshTokenService.class);
    private final EmailVerificationTokenRepository verifyRepo = mock(EmailVerificationTokenRepository.class);
    private final EmailService email                 = mock(EmailService.class);
    private final GoogleOAuthService googleOAuth     = mock(GoogleOAuthService.class);
    private final PasswordPolicy passwordPolicy      = new PasswordPolicy();
    private final AuditService audit                 = mock(AuditService.class);
    private final HttpServletRequest request         = mock(HttpServletRequest.class);
    private final Counter counter                    = mock(Counter.class);

    private final JwtService jwt = new JwtService(new JwtProperties(
            "test-secret-test-secret-test-secret-32bytes!!",
            Duration.ofHours(2), Duration.ofDays(7), Duration.ofMinutes(30)));

    private final AuthService service = new AuthService(
            users, encoder, jwt, refreshTokens, verifyRepo,
            email, googleOAuth, passwordPolicy, audit,
            counter, counter, counter, counter, counter);

    @Test
    void register_rejects_duplicate_email() {
        when(users.existsByEmail("a@b.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(
                new RegisterRequest("a@b.com", "ValidP@ss1", "A"), request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void register_rejects_weak_password() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        assertThatThrownBy(() -> service.register(
                new RegisterRequest("new@b.com", "password", "A"), request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void login_rejects_bad_password() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("a@b.com");
        u.setPasswordHash(encoder.encode("correct-horse"));
        when(users.findActiveByEmail("a@b.com")).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.com", "wrong"), request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void register_then_tokens_are_issued() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(refreshTokens.issue(any(), any())).thenReturn("fake-refresh-token");

        AuthResponse res = service.register(
                new RegisterRequest("new@b.com", "ValidP@ss1!", "New"), request);

        assertThat(res.accessToken()).isNotBlank();
        assertThat(res.refreshToken()).isNotBlank();
        assertThat(jwt.verify(res.accessToken(), JwtService.TokenType.ACCESS)).isNotNull();
    }

    @Test
    void login_increments_failed_attempts_on_bad_password() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("a@b.com");
        u.setPasswordHash(encoder.encode("correct"));

        when(users.findActiveByEmail("a@b.com")).thenReturn(Optional.of(u));
        when(users.save(any(User.class))).thenReturn(u);

        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.com", "wrong"), request));

        assertThat(u.getFailedLoginAttempts()).isEqualTo(1);
    }
}
