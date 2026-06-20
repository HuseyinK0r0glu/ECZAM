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

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

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
        String path = req.getServletPath();
        boolean limited = path.startsWith("/auth") || path.startsWith("/ai");
        if (limited) {
            String key = clientIp(req) + ":" + (path.startsWith("/auth") ? "auth" : "ai");
            Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(path));
            if (!bucket.tryConsume(1)) {
                res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                res.getWriter().write("{\"data\":null,\"meta\":null,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}}");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private Bucket newBucket(String path) {
        // auth: brute-force guard; ai: cost/abuse guard.
        int perMin = path.startsWith("/auth") ? authPerMinute : aiPerMinute;
        return Bucket.builder().addLimit(Bandwidth.simple(perMin, Duration.ofMinutes(1))).build();
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
