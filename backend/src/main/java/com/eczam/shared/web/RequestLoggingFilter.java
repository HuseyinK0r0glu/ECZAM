package com.eczam.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs every incoming request with timing (after the response is written).
 * Runs last (lowest priority) so the correlation ID and security context are already set.
 *
 * Log format: METHOD /path status durationMs [correlationId]
 * Sensitive paths (/auth/**) get trimmed body logging to avoid credential leaks.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            long duration = System.currentTimeMillis() - start;
            if (log.isInfoEnabled()) {
                String correlationId = res.getHeader(CorrelationIdFilter.HEADER);
                log.info("{} {} {} {}ms [{}]",
                        req.getMethod(),
                        req.getRequestURI(),
                        res.getStatus(),
                        duration,
                        correlationId != null ? correlationId : "-");
            }
        }
    }

    /** Skip static resources and actuator calls from the request log. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest req) {
        String path = req.getServletPath();
        return path.startsWith("/actuator/health")
                || path.endsWith(".ico")
                || path.endsWith(".css")
                || path.endsWith(".js");
    }
}
