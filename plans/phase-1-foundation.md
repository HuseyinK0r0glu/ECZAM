# Phase 1 — Foundation

> **Goal:** a runnable monorepo with the full database schema, working email/password
> auth (register, login, JWT access + refresh, password reset), a stateless Spring
> Security chain, and a React shell with routing, an auth context, and protected routes.
>
> **Realizes:** EP-01 · US-001…006 · FR-001…006 · UC-001.
> **Prerequisites:** [00-overview.md](00-overview.md) (toolchain, env vars).
> **Exit criteria:** register → log in → stay signed in → reach a protected dashboard.

---

## 1. Dependencies / setup

- Backend: Spring Boot 3.2+ (web, data-jpa, security, validation), Flyway, PostgreSQL
  driver, jjwt, Hibernate JSON, MapStruct, Lombok, springdoc, Testcontainers (test).
- Frontend: React 18, react-router-dom v6, @tanstack/react-query, zustand, axios,
  tailwindcss.

Generate a JWT secret: `openssl rand -base64 48`.

---

## 2. Backend

### `backend/docker-compose.yml`

```yaml
services:
  db:
    image: pgvector/pgvector:pg16
    container_name: eczam-db
    environment:
      POSTGRES_DB: eczam
      POSTGRES_USER: eczam
      POSTGRES_PASSWORD: eczam
    ports:
      - "5432:5432"
    volumes:
      - eczam_pgdata:/var/lib/postgresql/data
volumes:
  eczam_pgdata:
```

### `backend/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.eczam</groupId>
    <artifactId>eczam-backend</artifactId>
    <version>0.1.0</version>
    <name>eczam-backend</name>

    <properties>
        <java.version>21</java.version>
        <mapstruct.version>1.5.5.Final</mapstruct.version>
    </properties>

    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>

        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
        <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>

        <!-- JWT -->
        <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>0.12.5</version></dependency>
        <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>0.12.5</version><scope>runtime</scope></dependency>
        <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>0.12.5</version><scope>runtime</scope></dependency>

        <!-- MapStruct -->
        <dependency><groupId>org.mapstruct</groupId><artifactId>mapstruct</artifactId><version>${mapstruct.version}</version></dependency>

        <!-- Lombok -->
        <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>

        <!-- OpenAPI -->
        <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.5.0</version></dependency>

        <!-- Test -->
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.testcontainers</groupId><artifactId>testcontainers-bom</artifactId>
                <version>1.19.7</version><type>pom</type><scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes><exclude><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></exclude></excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><version>1.18.32</version></path>
                        <path><groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId><version>${mapstruct.version}</version></path>
                        <path><groupId>org.projectlombok</groupId><artifactId>lombok-mapstruct-binding</artifactId><version>0.2.0</version></path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### `backend/src/main/resources/application.yml`

```yaml
server:
  port: ${PORT:8080}
  servlet:
    context-path: /api/v1

spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/eczam}
    username: ${DATABASE_USER:eczam}
    password: ${DATABASE_PASSWORD:eczam}
  jpa:
    hibernate:
      ddl-auto: validate        # schema is owned by Flyway
    properties:
      hibernate.format_sql: false
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration

eczam:
  jwt:
    secret: ${JWT_SECRET:dev-secret-change-me-dev-secret-change-me-32b}
    access-ttl: PT2H            # 2 hours
    refresh-ttl: P7D           # 7 days
    reset-ttl: PT30M           # 30 minutes
  cors:
    allowed-origin: ${FRONTEND_URL:http://localhost:5173}

springdoc:
  swagger-ui:
    path: /swagger-ui

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### `backend/src/main/java/com/eczam/EczamApplication.java`

```java
package com.eczam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EczamApplication {
    public static void main(String[] args) {
        SpringApplication.run(EczamApplication.class, args);
    }
}
```

### Flyway migrations

These implement the authoritative schema from
[docs/database-design.md](../docs/database-design.md). The **full** schema (all 7 tables)
is applied in Phase 1 per the brief.

#### `backend/src/main/resources/db/migration/V1__extensions.sql`

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- gen_random_uuid()
```

#### `backend/src/main/resources/db/migration/V2__core_tables.sql`

```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   TEXT NOT NULL,
    display_name    VARCHAR(100),
    notification_preferences JSONB NOT NULL DEFAULT '{
        "push": true, "email": false,
        "low_stock_threshold": 7, "expiry_warning_days": 30
    }',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE medications (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(255) NOT NULL,
    generic_name     VARCHAR(255),
    manufacturer     VARCHAR(255),
    barcode          VARCHAR(100) UNIQUE,
    form             VARCHAR(50),
    strength         VARCHAR(50),
    leaflet_raw      TEXT,
    leaflet_sections JSONB,
    vector_indexed   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_medications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    medication_id   UUID NOT NULL REFERENCES medications(id),
    quantity        NUMERIC(10, 2) NOT NULL DEFAULT 0,
    unit            VARCHAR(20) NOT NULL DEFAULT 'pill',
    expiration_date DATE,
    notes           TEXT,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, medication_id, expiration_date)
);

CREATE TABLE medication_schedules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_medication_id  UUID NOT NULL REFERENCES user_medications(id) ON DELETE CASCADE,
    dosage_amount       NUMERIC(6, 2) NOT NULL,
    frequency_type      VARCHAR(20) NOT NULL,
    frequency_value     INTEGER,
    scheduled_times     TIME[] NOT NULL,
    days_of_week        SMALLINT[],
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    starts_on           DATE NOT NULL DEFAULT CURRENT_DATE,
    ends_on             DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE medication_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_medication_id  UUID NOT NULL REFERENCES user_medications(id) ON DELETE CASCADE,
    schedule_id         UUID REFERENCES medication_schedules(id) ON DELETE SET NULL,
    taken_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    quantity_used       NUMERIC(6, 2) NOT NULL,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE push_subscriptions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint    TEXT NOT NULL UNIQUE,
    p256dh      TEXT NOT NULL,
    auth        TEXT NOT NULL,
    user_agent  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE leaflet_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    medication_id   UUID NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    section_name    VARCHAR(100) NOT NULL,
    chunk_text      TEXT NOT NULL,
    embedding       VECTOR(1536),
    chunk_index     INTEGER NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

#### `backend/src/main/resources/db/migration/V3__indexes.sql`

```sql
CREATE INDEX idx_user_medications_user_id ON user_medications(user_id);
CREATE INDEX idx_user_medications_expiration ON user_medications(expiration_date)
    WHERE expiration_date IS NOT NULL;
CREATE INDEX idx_medication_schedules_active ON medication_schedules(user_medication_id)
    WHERE active = TRUE;
CREATE INDEX idx_medication_logs_user_med ON medication_logs(user_medication_id, taken_at DESC);
CREATE INDEX idx_push_subscriptions_user_id ON push_subscriptions(user_id);
CREATE INDEX idx_leaflet_chunks_medication ON leaflet_chunks(medication_id);
CREATE INDEX idx_leaflet_chunks_embedding ON leaflet_chunks
    USING hnsw (embedding vector_cosine_ops);
```

### Shared: response envelope & errors

#### `backend/src/main/java/com/eczam/shared/web/ApiResponse.java`

```java
package com.eczam.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(T data, Meta meta, ApiError error) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(data, null, null); }
    public static <T> ApiResponse<T> ok(T data, Meta meta) { return new ApiResponse<>(data, meta, null); }
    public static <T> ApiResponse<T> fail(ApiError error) { return new ApiResponse<>(null, null, error); }
}
```

#### `backend/src/main/java/com/eczam/shared/web/Meta.java`

```java
package com.eczam.shared.web;

public record Meta(String nextCursor, Integer limit) {}
```

#### `backend/src/main/java/com/eczam/shared/web/ApiError.java`

```java
package com.eczam.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, String> fields) {
    public ApiError(String code, String message) { this(code, message, null); }
}
```

#### `backend/src/main/java/com/eczam/shared/web/ErrorCode.java`

```java
package com.eczam.shared.web;

public final class ErrorCode {
    public static final String VALIDATION_FAILED   = "VALIDATION_FAILED";
    public static final String UNAUTHENTICATED     = "UNAUTHENTICATED";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String FORBIDDEN           = "FORBIDDEN";
    public static final String NOT_FOUND           = "NOT_FOUND";
    public static final String EMAIL_TAKEN         = "EMAIL_TAKEN";
    public static final String INVENTORY_BATCH_EXISTS = "INVENTORY_BATCH_EXISTS";
    public static final String INSUFFICIENT_STOCK  = "INSUFFICIENT_STOCK";
    public static final String BARCODE_NOT_FOUND   = "BARCODE_NOT_FOUND";
    public static final String RESET_TOKEN_INVALID = "RESET_TOKEN_INVALID";
    public static final String RATE_LIMITED        = "RATE_LIMITED";
    public static final String INTERNAL_ERROR      = "INTERNAL_ERROR";
    private ErrorCode() {}
}
```

#### `backend/src/main/java/com/eczam/shared/web/ApiException.java`

```java
package com.eczam.shared.web;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, message);
    }
}
```

#### `backend/src/main/java/com/eczam/shared/web/GlobalExceptionHandler.java`

```java
package com.eczam.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.status())
                .body(ApiResponse.fail(new ApiError(ex.code(), ex.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.fail(new ApiError(ErrorCode.VALIDATION_FAILED, "Validation failed", fields)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(new ApiError(ErrorCode.UNAUTHENTICATED, "Authentication required")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(new ApiError(ErrorCode.FORBIDDEN, "Access denied")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(new ApiError(ErrorCode.INTERNAL_ERROR, "Unexpected error")));
    }
}
```

#### `backend/src/main/java/com/eczam/shared/web/CursorCodec.java`

```java
package com.eczam.shared.web;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;

/** Opaque cursor: base64 of an ISO-8601 timestamp (newest-first lists). */
public final class CursorCodec {
    public static String encode(OffsetDateTime ts) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(ts.toString().getBytes(StandardCharsets.UTF_8));
    }
    public static OffsetDateTime decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return OffsetDateTime.parse(raw);
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Invalid cursor");
        }
    }
    private CursorCodec() {}
}
```

### Shared: security (JWT)

#### `backend/src/main/java/com/eczam/shared/security/JwtProperties.java`

```java
package com.eczam.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "eczam.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl, Duration resetTtl) {}
```

#### `backend/src/main/java/com/eczam/shared/security/JwtService.java`

```java
package com.eczam.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public enum TokenType { ACCESS, REFRESH, RESET }

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccess(UUID userId)  { return generate(userId, TokenType.ACCESS,  props.accessTtl().toSeconds()); }
    public String generateRefresh(UUID userId) { return generate(userId, TokenType.REFRESH, props.refreshTtl().toSeconds()); }
    public String generateReset(UUID userId)   { return generate(userId, TokenType.RESET,   props.resetTtl().toSeconds()); }

    private String generate(UUID userId, TokenType type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /** Returns the subject (userId) if valid and of the expected type, else throws. */
    public UUID verify(String token, TokenType expected) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        if (!expected.name().equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Wrong token type");
        }
        return UUID.fromString(claims.getSubject());
    }
}
```

#### `backend/src/main/java/com/eczam/shared/security/JwtAuthFilter.java`

```java
package com.eczam.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    public JwtAuthFilter(JwtService jwt) { this.jwt = jwt; }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                UUID userId = jwt.verify(header.substring(7), JwtService.TokenType.ACCESS);
                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
```

#### `backend/src/main/java/com/eczam/shared/security/SecurityConfig.java`

```java
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
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final String allowedOrigin;
    private final ObjectMapper mapper = new ObjectMapper();

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          @org.springframework.beans.factory.annotation.Value("${eczam.cors.allowed-origin}") String allowedOrigin) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.allowedOrigin = allowedOrigin;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(corsSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/actuator/health",
                                 "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                res.setStatus(HttpStatus.UNAUTHORIZED.value());
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                mapper.writeValue(res.getWriter(),
                    ApiResponse.fail(new ApiError(ErrorCode.UNAUTHENTICATED, "Authentication required")));
            }))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(allowedOrigin));
        cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
```

#### `backend/src/main/java/com/eczam/shared/security/CurrentUser.java`

```java
package com.eczam.shared.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.lang.annotation.*;

/** Injects the authenticated user's UUID (the JWT subject). */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
public @interface CurrentUser {}
```

> Usage: `public ... me(@CurrentUser UUID userId)`. The principal set by `JwtAuthFilter`
> is the `UUID`, so `@AuthenticationPrincipal` resolves it directly.

### Auth & users domain

#### `backend/src/main/java/com/eczam/users/NotificationPreferences.java`

```java
package com.eczam.users;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NotificationPreferences(
        boolean push,
        boolean email,
        @JsonProperty("low_stock_threshold") int lowStockThreshold,
        @JsonProperty("expiry_warning_days") int expiryWarningDays) {

    public static NotificationPreferences defaults() {
        return new NotificationPreferences(true, false, 7, 30);
    }
}
```

#### `backend/src/main/java/com/eczam/users/User.java`

```java
package com.eczam.users;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_preferences", nullable = false, columnDefinition = "jsonb")
    private NotificationPreferences notificationPreferences = NotificationPreferences.defaults();

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
```

#### `backend/src/main/java/com/eczam/users/UserRepository.java`

```java
package com.eczam.users;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

#### `backend/src/main/java/com/eczam/auth/dto/AuthDtos.java`

```java
package com.eczam.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Size(max = 100) String displayName) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record PasswordResetRequest(@Email @NotBlank String email) {}

    public record PasswordResetConfirm(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {}

    public record UserSummary(String id, String email, String displayName) {}

    public record AuthResponse(UserSummary user, String accessToken, String refreshToken) {}

    private AuthDtos() {}
}
```

#### `backend/src/main/java/com/eczam/auth/AuthService.java`

```java
package com.eczam.auth;

import com.eczam.auth.dto.AuthDtos.*;
import com.eczam.shared.security.JwtService;
import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import com.eczam.users.User;
import com.eczam.users.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw ApiException.conflict(ErrorCode.EMAIL_TAKEN, "Email already registered");
        }
        User u = new User();
        u.setEmail(req.email().toLowerCase());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setDisplayName(req.displayName());
        users.save(u);
        return tokensFor(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User u = users.findByEmail(req.email().toLowerCase())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return tokensFor(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        UUID userId;
        try {
            userId = jwt.verify(refreshToken, JwtService.TokenType.REFRESH);
        } catch (Exception e) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
        User u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        return tokensFor(u);
    }

    /** Always succeeds (non-enumerating). Returns a reset token to be emailed. */
    @Transactional(readOnly = true)
    public String requestReset(String email) {
        return users.findByEmail(email.toLowerCase())
                .map(u -> jwt.generateReset(u.getId()))
                .orElse(null); // caller still returns 204
    }

    @Transactional
    public void confirmReset(PasswordResetConfirm req) {
        UUID userId;
        try {
            userId = jwt.verify(req.token(), JwtService.TokenType.RESET);
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorCode.RESET_TOKEN_INVALID, "Invalid or expired reset token");
        }
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.badRequest(ErrorCode.RESET_TOKEN_INVALID, "Invalid reset token"));
        u.setPasswordHash(encoder.encode(req.newPassword()));
    }

    private AuthResponse tokensFor(User u) {
        var summary = new UserSummary(u.getId().toString(), u.getEmail(), u.getDisplayName());
        return new AuthResponse(summary, jwt.generateAccess(u.getId()), jwt.generateRefresh(u.getId()));
    }
}
```

#### `backend/src/main/java/com/eczam/auth/AuthController.java`

```java
package com.eczam.auth;

import com.eczam.auth.dto.AuthDtos.*;
import com.eczam.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(auth.register(req)));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(auth.login(req));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.ok(auth.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // Stateless JWT: client discards tokens. (Blocklist can be added later.)
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestReset(@Valid @RequestBody PasswordResetRequest req) {
        String token = auth.requestReset(req.email());
        // In production: email the link containing `token`. (Phase 4 adds the mailer.)
        // Never reveal whether the email exists.
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmReset(@Valid @RequestBody PasswordResetConfirm req) {
        auth.confirmReset(req);
    }
}
```

#### `backend/src/main/java/com/eczam/users/dto/UserDtos.java`

```java
package com.eczam.users.dto;

import com.eczam.users.NotificationPreferences;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    public record UserProfile(String id, String email, String displayName,
                              NotificationPreferences notificationPreferences) {}

    public record UpdateProfileRequest(@Size(max = 100) String displayName) {}

    public record UpdatePreferencesRequest(
            Boolean push, Boolean email,
            @Min(0) Integer lowStockThreshold,
            @Min(0) Integer expiryWarningDays) {}

    private UserDtos() {}
}
```

#### `backend/src/main/java/com/eczam/users/UserService.java`

```java
package com.eczam.users;

import com.eczam.shared.web.ApiException;
import com.eczam.users.dto.UserDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    public UserService(UserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        return toProfile(load(userId));
    }

    @Transactional
    public UserProfile updateProfile(UUID userId, UpdateProfileRequest req) {
        User u = load(userId);
        if (req.displayName() != null) u.setDisplayName(req.displayName());
        return toProfile(u);
    }

    @Transactional
    public UserProfile updatePreferences(UUID userId, UpdatePreferencesRequest req) {
        User u = load(userId);
        NotificationPreferences cur = u.getNotificationPreferences();
        u.setNotificationPreferences(new NotificationPreferences(
                req.push() != null ? req.push() : cur.push(),
                req.email() != null ? req.email() : cur.email(),
                req.lowStockThreshold() != null ? req.lowStockThreshold() : cur.lowStockThreshold(),
                req.expiryWarningDays() != null ? req.expiryWarningDays() : cur.expiryWarningDays()));
        return toProfile(u);
    }

    private User load(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private UserProfile toProfile(User u) {
        return new UserProfile(u.getId().toString(), u.getEmail(), u.getDisplayName(),
                u.getNotificationPreferences());
    }
}
```

#### `backend/src/main/java/com/eczam/users/UserController.java`

```java
package com.eczam.users;

import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import com.eczam.users.dto.UserDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService users;
    public UserController(UserService users) { this.users = users; }

    @GetMapping("/me")
    public ApiResponse<UserProfile> me(@CurrentUser UUID userId) {
        return ApiResponse.ok(users.getProfile(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfile> updateMe(@CurrentUser UUID userId,
                                             @Valid @RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(users.updateProfile(userId, req));
    }

    @PatchMapping("/me/preferences")
    public ApiResponse<UserProfile> updatePrefs(@CurrentUser UUID userId,
                                                @Valid @RequestBody UpdatePreferencesRequest req) {
        return ApiResponse.ok(users.updatePreferences(userId, req));
    }
}
```

### Backend tests

#### `backend/src/test/java/com/eczam/auth/AuthServiceTest.java`

```java
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
```

#### `backend/src/test/java/com/eczam/AbstractIntegrationTest.java`

```java
package com.eczam;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("eczam").withUsername("eczam").withPassword("eczam");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("eczam.jwt.secret", () -> "test-secret-test-secret-test-secret-32bytes!!");
    }
}
```

#### `backend/src/test/java/com/eczam/auth/AuthIntegrationTest.java`

```java
package com.eczam.auth;

import com.eczam.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;

    @Test
    void register_login_and_access_protected_endpoint() {
        var reg = rest.postForEntity("/api/v1/auth/register",
                Map.of("email", "it@b.com", "password", "password1", "displayName", "IT"),
                Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) reg.getBody().get("data");
        String token = (String) data.get("accessToken");
        assertThat(token).isNotBlank();

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        var me = rest.exchange("/api/v1/users/me", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void protected_endpoint_requires_auth() {
        var res = rest.getForEntity("/api/v1/users/me", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

---

## 3. Frontend

### `frontend/package.json`

```json
{
  "name": "eczam-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest"
  },
  "dependencies": {
    "@tanstack/react-query": "^5.40.0",
    "axios": "^1.7.2",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.23.1",
    "zustand": "^4.5.2"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.4.5",
    "@testing-library/react": "^16.0.0",
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@vitejs/plugin-react": "^4.3.1",
    "autoprefixer": "^10.4.19",
    "jsdom": "^24.1.0",
    "postcss": "^8.4.38",
    "tailwindcss": "^3.4.4",
    "typescript": "^5.4.5",
    "vite": "^5.2.0",
    "vitest": "^1.6.0"
  }
}
```

### `frontend/vite.config.ts`

```ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
  test: { environment: "jsdom", globals: true, setupFiles: "./src/test/setup.ts" },
});
```

### `frontend/tsconfig.json`

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "baseUrl": "src"
  },
  "include": ["src"]
}
```

### `frontend/tailwind.config.js`

```js
/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontSize: { base: "1.125rem" }, // larger default for accessibility (P1)
    },
  },
  plugins: [],
};
```

### `frontend/postcss.config.js`

```js
export default { plugins: { tailwindcss: {}, autoprefixer: {} } };
```

### `frontend/index.html`

```html
<!doctype html>
<html lang="tr">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>ECZAM</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

### `frontend/src/index.css`

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

/* High-contrast, large-text friendly defaults (NFR-010..014) */
:root { color-scheme: light; }
button:focus-visible, a:focus-visible, input:focus-visible {
  outline: 3px solid #1d4ed8; outline-offset: 2px;
}
```

### `frontend/src/types.ts`

```ts
export interface ApiResponse<T> {
  data: T | null;
  meta: { nextCursor?: string; limit?: number } | null;
  error: { code: string; message: string; fields?: Record<string, string> } | null;
}

export interface UserSummary { id: string; email: string; displayName?: string; }
export interface AuthResponse { user: UserSummary; accessToken: string; refreshToken: string; }
```

### `frontend/src/services/apiClient.ts`

```ts
import axios, { AxiosError } from "axios";

const BASE = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api/v1";

export const apiClient = axios.create({ baseURL: BASE, headers: { "Content-Type": "application/json" } });

const ACCESS_KEY = "eczam.access";
const REFRESH_KEY = "eczam.refresh";

export const tokenStore = {
  access: () => localStorage.getItem(ACCESS_KEY),
  refresh: () => localStorage.getItem(REFRESH_KEY),
  set: (access: string, refresh: string) => {
    localStorage.setItem(ACCESS_KEY, access);
    localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear: () => { localStorage.removeItem(ACCESS_KEY); localStorage.removeItem(REFRESH_KEY); },
};

apiClient.interceptors.request.use((config) => {
  const t = tokenStore.access();
  if (t) config.headers.Authorization = `Bearer ${t}`;
  return config;
});

// On 401, try one refresh, then replay; otherwise clear and bubble up.
let refreshing: Promise<string> | null = null;
apiClient.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const original = error.config!;
    const refresh = tokenStore.refresh();
    if (error.response?.status === 401 && refresh && !(original as any)._retry) {
      (original as any)._retry = true;
      try {
        refreshing ??= apiClient
          .post<ApiAuth>("/auth/refresh", { refreshToken: refresh })
          .then((res) => {
            const d = res.data.data!;
            tokenStore.set(d.accessToken, d.refreshToken);
            return d.accessToken;
          })
          .finally(() => { refreshing = null; });
        const newAccess = await refreshing;
        original.headers!.Authorization = `Bearer ${newAccess}`;
        return apiClient(original);
      } catch {
        tokenStore.clear();
      }
    }
    return Promise.reject(error);
  }
);

interface ApiAuth { data: { accessToken: string; refreshToken: string } | null; }
```

### `frontend/src/services/authService.ts`

```ts
import { apiClient, tokenStore } from "./apiClient";
import type { ApiResponse, AuthResponse, UserSummary } from "../types";

export async function register(email: string, password: string, displayName?: string) {
  const res = await apiClient.post<ApiResponse<AuthResponse>>("/auth/register", { email, password, displayName });
  return persist(res.data.data!);
}

export async function login(email: string, password: string) {
  const res = await apiClient.post<ApiResponse<AuthResponse>>("/auth/login", { email, password });
  return persist(res.data.data!);
}

export async function fetchMe(): Promise<UserSummary> {
  const res = await apiClient.get<ApiResponse<UserSummary>>("/users/me");
  return res.data.data!;
}

export function logout() { tokenStore.clear(); }

function persist(auth: AuthResponse): UserSummary {
  tokenStore.set(auth.accessToken, auth.refreshToken);
  return auth.user;
}
```

### `frontend/src/contexts/AuthContext.tsx`

```tsx
import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { fetchMe, login as svcLogin, logout as svcLogout, register as svcRegister } from "../services/authService";
import { tokenStore } from "../services/apiClient";
import type { UserSummary } from "../types";

interface AuthState {
  user: UserSummary | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName?: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!tokenStore.access()) { setLoading(false); return; }
    fetchMe().then(setUser).catch(() => tokenStore.clear()).finally(() => setLoading(false));
  }, []);

  const value: AuthState = {
    user,
    loading,
    login: async (e, p) => setUser(await svcLogin(e, p)),
    register: async (e, p, d) => setUser(await svcRegister(e, p, d)),
    logout: () => { svcLogout(); setUser(null); },
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
```

### `frontend/src/routes/ProtectedRoute.tsx`

```tsx
import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";

export default function ProtectedRoute() {
  const { user, loading } = useAuth();
  if (loading) return <div className="p-8 text-xl">Yükleniyor…</div>;
  return user ? <Outlet /> : <Navigate to="/login" replace />;
}
```

### `frontend/src/pages/Login.tsx`

```tsx
import { useState, type FormEvent } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";

export default function Login() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try { await login(email, password); nav("/"); }
    catch { setError("E-posta veya şifre hatalı."); }
  }

  return (
    <main className="mx-auto max-w-md p-6">
      <h1 className="mb-6 text-3xl font-bold">Giriş Yap</h1>
      <form onSubmit={onSubmit} className="space-y-4" aria-describedby={error ? "err" : undefined}>
        <label className="block">
          <span className="text-lg">E-posta</span>
          <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
                 className="mt-1 w-full rounded border p-3 text-lg" />
        </label>
        <label className="block">
          <span className="text-lg">Şifre</span>
          <input type="password" required value={password} onChange={(e) => setPassword(e.target.value)}
                 className="mt-1 w-full rounded border p-3 text-lg" />
        </label>
        {error && <p id="err" role="alert" className="text-red-700">{error}</p>}
        <button type="submit" className="w-full rounded bg-blue-700 p-3 text-lg font-semibold text-white">
          Giriş Yap
        </button>
      </form>
      <p className="mt-4 text-lg">
        Hesabın yok mu? <Link className="text-blue-700 underline" to="/register">Kayıt ol</Link>
      </p>
    </main>
  );
}
```

### `frontend/src/pages/Register.tsx`

```tsx
import { useState, type FormEvent } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";

export default function Register() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try { await register(email, password, displayName || undefined); nav("/"); }
    catch (err: any) {
      const code = err?.response?.data?.error?.code;
      setError(code === "EMAIL_TAKEN" ? "Bu e-posta zaten kayıtlı." : "Kayıt başarısız. Bilgileri kontrol edin.");
    }
  }

  return (
    <main className="mx-auto max-w-md p-6">
      <h1 className="mb-6 text-3xl font-bold">Kayıt Ol</h1>
      <form onSubmit={onSubmit} className="space-y-4">
        <label className="block">
          <span className="text-lg">Ad (isteğe bağlı)</span>
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)}
                 className="mt-1 w-full rounded border p-3 text-lg" />
        </label>
        <label className="block">
          <span className="text-lg">E-posta</span>
          <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
                 className="mt-1 w-full rounded border p-3 text-lg" />
        </label>
        <label className="block">
          <span className="text-lg">Şifre (en az 8 karakter)</span>
          <input type="password" required minLength={8} value={password}
                 onChange={(e) => setPassword(e.target.value)}
                 className="mt-1 w-full rounded border p-3 text-lg" />
        </label>
        {error && <p role="alert" className="text-red-700">{error}</p>}
        <button type="submit" className="w-full rounded bg-blue-700 p-3 text-lg font-semibold text-white">
          Kayıt Ol
        </button>
      </form>
      <p className="mt-4 text-lg">
        Zaten hesabın var mı? <Link className="text-blue-700 underline" to="/login">Giriş yap</Link>
      </p>
    </main>
  );
}
```

### `frontend/src/pages/Dashboard.tsx` (placeholder — finalized in Phase 6)

```tsx
import { useAuth } from "../contexts/AuthContext";
import { Link } from "react-router-dom";

export default function Dashboard() {
  const { user, logout } = useAuth();
  return (
    <main className="mx-auto max-w-2xl p-6">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-3xl font-bold">Merhaba{user?.displayName ? `, ${user.displayName}` : ""}</h1>
        <button onClick={logout} className="rounded border px-4 py-2 text-lg">Çıkış</button>
      </div>
      <p className="text-lg text-gray-700">
        Panel Faz 6'da tamamlanacak. Şimdilik kimlik doğrulama çalışıyor.
      </p>
      <nav className="mt-6 flex flex-col gap-2 text-lg">
        <Link className="text-blue-700 underline" to="/inventory">Envanter (Faz 2)</Link>
      </nav>
    </main>
  );
}
```

### `frontend/src/App.tsx`

```tsx
import { Routes, Route, Navigate } from "react-router-dom";
import ProtectedRoute from "./routes/ProtectedRoute";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Dashboard from "./pages/Dashboard";

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/" element={<Dashboard />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
```

### `frontend/src/main.tsx`

```tsx
import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "./contexts/AuthContext";
import App from "./App";
import "./index.css";

const queryClient = new QueryClient();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
);
```

### `frontend/src/test/setup.ts`

```ts
import "@testing-library/jest-dom";
```

---

## 4. Exit criteria (Phase 1)

- [ ] `docker compose up -d` + `./mvnw spring-boot:run` starts the API; Flyway applies V1–V3.
- [ ] `POST /api/v1/auth/register` creates a user (bcrypt hash) and returns tokens; duplicate → 409.
- [ ] `POST /api/v1/auth/login` returns tokens; bad creds → 401 (non-enumerating).
- [ ] `GET /api/v1/users/me` works with a Bearer token; 401 without.
- [ ] `POST /api/v1/auth/refresh` issues a fresh access token.
- [ ] Password-reset request returns 204 always; confirm with a valid token changes the password.
- [ ] Frontend: register → land on dashboard; refresh page stays signed in; logout returns to /login.

## 5. Tests (Phase 1)

```bash
cd backend && ./mvnw test     # AuthServiceTest (unit) + AuthIntegrationTest (Testcontainers)
cd frontend && npm test       # add component tests for Login/Register as needed
```

Covers FR-001…006, NFR-041 (bcrypt), NFR-045 (422 validation), NFR-081 (endpoint integration).
Next: [phase-2-core-inventory.md](phase-2-core-inventory.md).
