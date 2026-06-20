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

    @Test
    void register_login_and_access_protected_endpoint() {
        var reg = rest.postForEntity("/auth/register",
                Map.of("email", "it@b.com", "password", "password1", "displayName", "IT"),
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
}
