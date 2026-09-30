package com.darshangohil.urlshortener.web.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts events per key in fixed time windows, in memory. Simple and good enough for
 * one instance; several instances would need a shared store such as Redis.
 *
 * <p>Expired windows are swept out now and then so the map can't grow without bound.
 */
public class FixedWindowRateLimiter {

    /** How a key stands: whether it is over its limit, and when that clears. */
    public record Status(boolean limited, Duration retryAfter) {
        public long retryAfterMinutes() {
            return Math.max(1, (retryAfter.toSeconds() + 59) / 60);
        }
    }

    private record Window(Instant start, AtomicInteger count) {
    }

    private static final int SWEEP_EVERY = 1_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger operations = new AtomicInteger();
    private final Duration windowLength;
    private final Clock clock;

    public FixedWindowRateLimiter(Duration windowLength, Clock clock) {
        this.windowLength = windowLength;
        this.clock = clock;
    }

    /** Counts one event and reports whether the key is now over {@code limit}. */
    public Status hit(String key, int limit) {
        Window window = current(key, true);
        int count = window.count().incrementAndGet();
        return status(window, count > limit);
    }

    /** Reports whether the key has already used up {@code limit}, without counting. */
    public Status check(String key, int limit) {
        Window window = current(key, false);
        return window == null ? new Status(false, Duration.ZERO) : status(window, window.count().get() >= limit);
    }

    public void reset(String key) {
        windows.remove(key);
    }

    private Window current(String key, boolean create) {
        if (operations.incrementAndGet() % SWEEP_EVERY == 0) {
            sweep();
        }
        Instant now = clock.instant();
        if (!create) {
            Window window = windows.get(key);
            return window == null || isExpired(window, now) ? null : window;
        }
        return windows.compute(key, (k, window) ->
                window == null || isExpired(window, now) ? new Window(now, new AtomicInteger()) : window);
    }

    private Status status(Window window, boolean limited) {
        Duration left = Duration.between(clock.instant(), window.start().plus(windowLength));
        return new Status(limited, left.isNegative() ? Duration.ZERO : left);
    }

    private boolean isExpired(Window window, Instant now) {
        return !now.isBefore(window.start().plus(windowLength));
    }

    void sweep() {
        Instant now = clock.instant();
        windows.values().removeIf(window -> isExpired(window, now));
    }

    int size() {
        return windows.size();
    }
}
