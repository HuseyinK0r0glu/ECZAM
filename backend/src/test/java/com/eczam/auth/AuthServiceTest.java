package com.eczam.auth;

import com.eczam.auth.dto.AuthDtos.*;
import com.eczam.shared.security.JwtProperties;
import com.eczam.shared.security.JwtService;
import com.eczam.shared.web.ApiException;
import com.eczam.users.User;
import com.eczam.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtService jwt = new JwtService(new JwtProperties(
            "test-secret-test-secret-test-secret-32bytes!!",
            Duration.ofHours(2), Duration.ofDays(7), Duration.ofMinutes(30)));
    private final AuthService service = new AuthService(users, encoder, jwt);

    @Test
    void register_rejects_duplicate_email() {
        when(users.existsByEmail("a@b.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest("a@b.com", "password1", "A")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void login_rejects_bad_password() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("a@b.com");
        u.setPasswordHash(encoder.encode("correct-horse"));
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.com", "wrong")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void register_then_tokens_are_issued() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0); u.setId(UUID.randomUUID()); return u;
        });
        AuthResponse res = service.register(new RegisterRequest("new@b.com", "password1", "New"));
        assertThat(res.accessToken()).isNotBlank();
        assertThat(res.refreshToken()).isNotBlank();
        assertThat(jwt.verify(res.accessToken(), JwtService.TokenType.ACCESS)).isNotNull();
    }
}
