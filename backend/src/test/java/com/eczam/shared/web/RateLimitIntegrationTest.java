package com.eczam.shared.web;

import com.eczam.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Low limit in an isolated context so it can't interfere with other integration tests. */
@TestPropertySource(properties = "eczam.ratelimit.auth-per-minute=3")
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;

    private HttpStatus register() {
        var res = rest.postForEntity("/auth/register",
                Map.of("email", UUID.randomUUID() + "@rl.com", "password", "password1", "displayName", "RL"),
                Map.class);
        return HttpStatus.valueOf(res.getStatusCode().value());
    }

    @Test
    void auth_endpoint_returns_429_after_the_limit() {
        // First 3 (the configured limit) are allowed.
        for (int i = 0; i < 3; i++) {
            assertThat(register()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
        // The 4th within the minute is rate-limited.
        var res = rest.postForEntity("/auth/register",
                Map.of("email", UUID.randomUUID() + "@rl.com", "password", "password1", "displayName", "RL"),
                Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) res.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("RATE_LIMITED");
    }
}
