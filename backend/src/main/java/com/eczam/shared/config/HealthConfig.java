package com.eczam.shared.config;

import com.eczam.shared.security.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Custom health indicators exposed at GET /actuator/health.
 */
@Slf4j
@Configuration
public class HealthConfig {

    /**
     * Verifies the JWT secret meets minimum security requirements at startup.
     * If the secret is too short or still the default, the application starts
     * but the health check marks itself DOWN (safe to gate deployments on health).
     */
    @Bean
    public HealthIndicator jwtSecretHealth(JwtProperties jwtProperties) {
        return () -> {
            String secret = jwtProperties.secret();
            if (secret == null || secret.length() < 32) {
                return Health.down()
                        .withDetail("reason", "JWT_SECRET is too short (min 32 characters)")
                        .build();
            }
            if (secret.startsWith("dev-secret")) {
                return Health.down()
                        .withDetail("reason", "JWT_SECRET is still the default dev value — set a real secret in production")
                        .build();
            }
            return Health.up().build();
        };
    }

    /**
     * Checks that the database has the pgvector extension loaded
     * (required for the AI RAG pipeline).
     */
    @Bean
    public HealthIndicator pgvectorHealth(JdbcTemplate jdbc) {
        return () -> {
            try {
                Integer count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                        Integer.class);
                if (count != null && count > 0) {
                    return Health.up().withDetail("extension", "pgvector").build();
                }
                return Health.down().withDetail("reason", "pgvector extension not installed").build();
            } catch (Exception e) {
                return Health.down().withDetail("error", e.getMessage()).build();
            }
        };
    }
}
