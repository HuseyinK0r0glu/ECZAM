package com.eczam.auth;

import com.eczam.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;

    // ---- Registration / Login ----

    @Test
    void register_login_and_access_protected_endpoint() {
        var reg = rest.postForEntity("/auth/register",
                Map.of("email", "it@b.com", "password", "ValidP@ss1!", "displayName", "IT"),
                Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) reg.getBody().get("data");
        String token = (String) data.get("accessToken");
        assertThat(token).isNotBlank();

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        var me = rest.exchange("/users/me", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void protected_endpoint_requires_auth() {
        var res = rest.getForEntity("/users/me", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void register_rejects_weak_password() {
        var reg = rest.postForEntity("/auth/register",
                Map.of("email", "weak@b.com", "password", "password", "displayName", "Weak"),
                Map.class);
        // Bean Validation passes (length >= 8) but PasswordPolicy rejects it
        assertThat(reg.getStatusCode()).isIn(
                HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.CONFLICT);
    }

    @Test
    void register_rejects_duplicate_email() {
        rest.postForEntity("/auth/register",
                Map.of("email", "dup@b.com", "password", "ValidP@ss1!", "displayName", "Dup"),
                Map.class);
        var reg2 = rest.postForEntity("/auth/register",
                Map.of("email", "dup@b.com", "password", "ValidP@ss1!", "displayName", "Dup2"),
                Map.class);
        assertThat(reg2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- Refresh token rotation ----

    @Test
    void refresh_token_rotation_issues_new_tokens() {
        // Register to get tokens
        var reg = rest.postForEntity("/auth/register",
                Map.of("email", "refresh@b.com", "password", "ValidP@ss1!", "displayName", "R"),
                Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) reg.getBody().get("data");
        String refreshToken = (String) data.get("refreshToken");
        String firstAccess = (String) data.get("accessToken");

        // Use refresh token to get new tokens
        var refresh = rest.postForEntity("/auth/refresh",
                Map.of("refreshToken", refreshToken), Map.class);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> newData = (Map<String, Object>) refresh.getBody().get("data");
        String newAccess  = (String) newData.get("accessToken");
        String newRefresh = (String) newData.get("refreshToken");

        assertThat(newAccess).isNotBlank().isNotEqualTo(firstAccess);
        assertThat(newRefresh).isNotBlank().isNotEqualTo(refreshToken);
    }

    @Test
    void reusing_old_refresh_token_is_rejected() {
        var reg = rest.postForEntity("/auth/register",
                Map.of("email", "reuse@b.com", "password", "ValidP@ss1!", "displayName", "Reuse"),
                Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) reg.getBody().get("data");
        String refreshToken = (String) data.get("refreshToken");

        // First use (valid)
        rest.postForEntity("/auth/refresh", Map.of("refreshToken", refreshToken), Map.class);

        // Second use of same token (should be rejected)
        var reuse = rest.postForEntity("/auth/refresh",
                Map.of("refreshToken", refreshToken), Map.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- Password reset ----

    @Test
    void password_reset_request_always_returns_204() {
        // For non-existent email — non-enumerating
        var res = rest.postForEntity("/auth/password-reset/request",
                Map.of("email", "nonexistent@b.com"), Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // For existing email
        rest.postForEntity("/auth/register",
                Map.of("email", "reset@b.com", "password", "ValidP@ss1!"), Map.class);
        var res2 = rest.postForEntity("/auth/password-reset/request",
                Map.of("email", "reset@b.com"), Void.class);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ---- Sessions ----

    @Test
    void list_sessions_returns_active_sessions() {
        var reg = rest.postForEntity("/auth/register",
                Map.of("email", "sessions@b.com", "password", "ValidP@ss1!"), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) reg.getBody().get("data");
        String token = (String) data.get("accessToken");

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        var sessions = rest.exchange("/auth/sessions", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(sessions.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        var sessionList = (java.util.List<?>) sessions.getBody().get("data");
        assertThat(sessionList).isNotEmpty();
    }

    // ---- Account lockout ----

    @Test
    void account_gets_locked_after_too_many_failed_logins() {
        rest.postForEntity("/auth/register",
                Map.of("email", "lockme@b.com", "password", "ValidP@ss1!"), Map.class);

        // Fire 5 bad login attempts
        for (int i = 0; i < 5; i++) {
            rest.postForEntity("/auth/login",
                    Map.of("email", "lockme@b.com", "password", "wrong"), Map.class);
        }

        // 6th attempt (even with correct password) should get LOCKED or UNAUTHORIZED
        var locked = rest.postForEntity("/auth/login",
                Map.of("email", "lockme@b.com", "password", "ValidP@ss1!"), Map.class);
        assertThat(locked.getStatusCode()).isIn(
                HttpStatus.UNAUTHORIZED, HttpStatus.TOO_MANY_REQUESTS);
    }
}
