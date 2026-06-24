package com.eczam.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes append-only audit log entries asynchronously so the main request
 * path is never blocked by I/O to the audit table (KVKK SEC-K09).
 *
 * If called from within an active transaction the write is deferred until
 * AFTER that transaction commits via TransactionSynchronization.afterCommit().
 * This prevents the FK-violation race where the user row is not yet visible
 * to the audit writer because the outer transaction hasn't committed yet.
 *
 * Each audit write runs in its own REQUIRES_NEW transaction on the
 * auditExecutor thread pool so it is fully isolated from the caller.
 */
@Slf4j
@Service
public class AuditService {

    private final AuditLogRepository repo;
    private final TaskExecutor auditExecutor;
    private final TransactionTemplate requiresNewTx;

    public AuditService(AuditLogRepository repo,
                        @Qualifier("auditExecutor") TaskExecutor auditExecutor,
                        TransactionTemplate requiresNewTx) {
        this.repo         = repo;
        this.auditExecutor = auditExecutor;
        this.requiresNewTx = requiresNewTx;
    }

    /**
     * Schedule an audit write.
     * - Called inside a transaction → defers until after commit.
     * - Called outside a transaction → submits to the audit executor immediately.
     */
    public void log(String eventType, UUID userId, HttpServletRequest request,
                    Map<String, Object> details) {
        // Capture request fields eagerly (request object may be recycled after servlet returns)
        String ip = request != null ? extractIp(request) : null;
        String ua = request != null ? truncate(request.getHeader("User-Agent"), 250) : null;

        Runnable writer = () -> submit(eventType, userId, ip, ua, details);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // Defer until AFTER the outer transaction commits so FK target rows are visible
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    writer.run();
                }
            });
        } else {
            writer.run();
        }
    }

    /** Convenience overload: no extra details. */
    public void log(String eventType, UUID userId, HttpServletRequest request) {
        log(eventType, userId, request, null);
    }

    /** Convenience builder for inline detail maps. */
    public static Map<String, Object> details(Object... keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException("Must pass key-value pairs");
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Submit the write to the dedicated thread pool. */
    private void submit(String eventType, UUID userId, String ip, String ua,
                        Map<String, Object> details) {
        auditExecutor.execute(() -> writeInNewTx(eventType, userId, ip, ua, details));
    }

    /** Execute the INSERT in its own REQUIRES_NEW transaction. */
    private void writeInNewTx(String eventType, UUID userId, String ip, String ua,
                               Map<String, Object> details) {
        try {
            requiresNewTx.executeWithoutResult(status -> {
                AuditLog entry = new AuditLog();
                entry.setEventType(eventType);
                entry.setUserId(userId);
                entry.setDetails(details);
                entry.setIpAddress(ip);
                entry.setUserAgent(ua);
                repo.save(entry);
            });
        } catch (Exception ex) {
            // Audit failure must never surface to the caller
            log.error("Audit log write failed for event={}: {}", eventType, ex.getMessage());
        }
    }

    private static String extractIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
