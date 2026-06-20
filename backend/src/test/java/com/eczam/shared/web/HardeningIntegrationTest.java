package com.eczam.shared.web;

import com.eczam.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Security headers + actuator health (rate limiting is effectively disabled in this shared context). */
class HardeningIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;

    @Test
    void responses_carry_security_headers() {
        var res = rest.getForEntity("/actuator/health", Map.class);
        var headers = res.getHeaders();
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(headers.getFirst("Content-Security-Policy")).contains("default-src 'self'");
    }

    @Test
    void actuator_health_is_up() {
        var res = rest.getForEntity("/actuator/health", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("status")).isEqualTo("UP");
    }
}
