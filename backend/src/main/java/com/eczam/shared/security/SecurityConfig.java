package com.eczam.shared.security;

import com.eczam.shared.web.ApiError;
import com.eczam.shared.web.ApiResponse;
import com.eczam.shared.web.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@EnableMethodSecurity   // enables @PreAuthorize / @PostAuthorize on service and controller methods
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final List<String> allowedOrigins;
    private final int bcryptStrength;
    private final ObjectMapper mapper = new ObjectMapper();

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          @org.springframework.beans.factory.annotation.Value("${eczam.cors.allowed-origins:${eczam.cors.allowed-origin:http://localhost:5173}}") String allowedOriginsRaw,
                          @org.springframework.beans.factory.annotation.Value("${eczam.security.bcrypt-strength:12}") int bcryptStrength) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.bcryptStrength = bcryptStrength;
        // Support multiple origins separated by commas
        this.allowedOrigins = List.of(allowedOriginsRaw.split(","))
                .stream().map(String::strip).filter(s -> !s.isBlank()).toList();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(corsSource()))
            .headers(h -> h
                .frameOptions(fo -> fo.deny())
                .contentTypeOptions(co -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .referrerPolicy(rp -> rp.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
                    "Content-Security-Policy",
                    "default-src 'self'; img-src 'self' data:; connect-src 'self'; " +
                    "script-src 'self'; style-src 'self' 'unsafe-inline'"))
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
                    "Permissions-Policy",
                    "camera=(), microphone=(), geolocation=()")))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers(
                    "/auth/register",
                    "/auth/login",
                    "/auth/google",
                    "/auth/refresh",
                    "/auth/password-reset/request",
                    "/auth/password-reset/confirm",
                    "/auth/verify-email",
                    "/actuator/health",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                // Admin-only endpoints
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Everything else requires authentication
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(HttpStatus.UNAUTHORIZED.value());
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(res.getWriter(),
                        ApiResponse.fail(new ApiError(ErrorCode.UNAUTHENTICATED, "Authentication required")));
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(HttpStatus.FORBIDDEN.value());
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(res.getWriter(),
                        ApiResponse.fail(new ApiError(ErrorCode.FORBIDDEN, "Access denied")));
                }))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // cost injected via eczam.security.bcrypt-strength (default 12; set to 4 in tests for speed)
        return new BCryptPasswordEncoder(bcryptStrength);
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(allowedOrigins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("X-Correlation-Id"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
