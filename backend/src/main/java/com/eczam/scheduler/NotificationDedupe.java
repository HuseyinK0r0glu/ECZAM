package com.eczam.scheduler;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory guard against duplicate sends within a window (NFR-020).
 * For multi-instance deployments use a persistent store + ShedLock instead.
 */
@Component
public class NotificationDedupe {
    private final ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>();
    private volatile LocalDate day = LocalDate.now();

    public boolean firstTimeToday(String key) {
        rollover();
        return seen.putIfAbsent(key, Boolean.TRUE) == null;
    }
    public boolean firstTimeForMinute(String key) {
        return seen.putIfAbsent(key, Boolean.TRUE) == null;
    }
    private void rollover() {
        LocalDate now = LocalDate.now();
        if (!now.equals(day)) { synchronized (this) { if (!now.equals(day)) { seen.clear(); day = now; } } }
    }
}
