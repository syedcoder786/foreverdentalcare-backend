package com.clinic.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory fixed-window rate limiter keyed by an arbitrary string (e.g. "login:203.0.113.7").
 * Suitable for a single-instance deployment. For multiple instances, replace with a shared store (e.g. Redis).
 */
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Result of an attempt: whether it is allowed and, if not, seconds until the window resets. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    public Decision tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long windowMs = window.toMillis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> now - e.getValue().start >= windowMs);
        }
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= windowMs) {
                return new Window(now, 1);
            }
            existing.count++;
            return existing;
        });
        if (w.count <= limit) {
            return new Decision(true, 0);
        }
        long retryAfter = Math.max(1, (w.start + windowMs - now + 999) / 1000);
        return new Decision(false, retryAfter);
    }

    public void reset() {
        windows.clear();
    }

    private static final class Window {
        private final long start;
        private int count;

        private Window(long start, int count) {
            this.start = start;
            this.count = count;
        }
    }
}
