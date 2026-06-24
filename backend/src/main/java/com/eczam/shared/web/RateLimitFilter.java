package com.eczam.shared.web;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter using Bucket4j (sliding window per IP).
 *
 * Limits applied:
 *  - Auth endpoints (POST /auth/**)        : authPerMinute per IP   (brute-force guard)
 *  - AI endpoints  (POST /ai/**)           : aiPerMinute   per IP   (cost/abuse guard)
 *  - Password-change / verify-resend       : 5 per 15 min  per IP   (abuse guard)
 *
 * For production at scale, replace ConcurrentHashMap with a distributed cache (Redis + Bucket4j-redis).
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String TOO_MANY = "{\"data\":null,\"meta\":null,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests — please wait before trying again\"}}";

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int authPerMinute;
    private final int aiPerMinute;

    public RateLimitFilter(@Value("${eczam.ratelimit.auth-per-minute:10}") int authPerMinute,
                           @Value("${eczam.ratelimit.ai-per-minute:20}") int aiPerMinute) {
        this.authPerMinute = authPerMinute;
        this.aiPerMinute = aiPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path   = req.getServletPath();
        String method = req.getMethod();
        String ip     = clientIp(req);

        BucketType type = classify(path, method);
        if (type != null) {
            String key = ip + ":" + type.name();
            Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(type));
            if (!bucket.tryConsume(1)) {
                res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                res.setHeader("Retry-After", "60");
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                res.getWriter().write(TOO_MANY);
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private BucketType classify(String path, String method) {
        if (!method.equalsIgnoreCase("POST") && !method.equalsIgnoreCase("DELETE")) return null;
        if (path.startsWith("/auth/login") || path.startsWith("/auth/register")
                || path.startsWith("/auth/password-reset") || path.startsWith("/auth/google")) {
            return BucketType.AUTH;
        }
        if (path.startsWith("/auth/change-password")
                || path.startsWith("/auth/resend-verification")
                || path.startsWith("/users/me/change-email")) {
            return BucketType.SENSITIVE;
        }
        if (path.startsWith("/ai/")) {
            return BucketType.AI;
        }
        return null;
    }

    private Bucket newBucket(BucketType type) {
        return switch (type) {
            // refillIntervally = strict fixed window (all tokens refill at once after period)
            case AUTH      -> Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(authPerMinute)
                            .refillIntervally(authPerMinute, Duration.ofMinutes(1))
                            .build())
                    .build();
            case SENSITIVE -> Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(5)
                            .refillIntervally(5, Duration.ofMinutes(15))
                            .build())
                    .build();
            case AI        -> Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(aiPerMinute)
                            .refillIntervally(aiPerMinute, Duration.ofMinutes(1))
                            .build())
                    .build();
        };
    }

    private enum BucketType { AUTH, SENSITIVE, AI }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
