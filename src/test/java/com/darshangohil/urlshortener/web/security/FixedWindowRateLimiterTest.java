package com.darshangohil.urlshortener.web.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    /** A clock the test can move forward. */
    static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-09-30T12:00:00Z");

        void advance(Duration duration) { now = now.plus(duration); }
        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private final TestClock clock = new TestClock();
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(Duration.ofMinutes(10), clock);

    @Test
    void allowsUpToTheLimitThenRefuses() {
        assertThat(limiter.hit("k", 3).limited()).isFalse();
        assertThat(limiter.hit("k", 3).limited()).isFalse();
        assertThat(limiter.hit("k", 3).limited()).isFalse();
        assertThat(limiter.hit("k", 3).limited()).isTrue();
    }

    @Test
    void theWindowResetsAfterItsLength() {
        for (int i = 0; i < 4; i++) {
            limiter.hit("k", 3);
        }
        clock.advance(Duration.ofMinutes(4));
        assertThat(limiter.hit("k", 3).retryAfter()).isEqualTo(Duration.ofMinutes(6));

        clock.advance(Duration.ofMinutes(6));
        assertThat(limiter.hit("k", 3).limited()).isFalse();
    }

    @Test
    void keysAreCountedSeparately() {
        limiter.hit("a", 1);
        assertThat(limiter.hit("a", 1).limited()).isTrue();
        assertThat(limiter.hit("b", 1).limited()).isFalse();
    }

    @Test
    void checkDoesNotCount() {
        limiter.hit("k", 2);
        assertThat(limiter.check("k", 2).limited()).isFalse();
        assertThat(limiter.check("k", 2).limited()).isFalse();
        limiter.hit("k", 2);
        assertThat(limiter.check("k", 2).limited()).isTrue();
    }

    @Test
    void retryAfterRoundsUpToWholeMinutes() {
        limiter.hit("k", 1);
        clock.advance(Duration.ofSeconds(9 * 60 + 30));   // 30 seconds left

        assertThat(limiter.check("k", 1).retryAfterMinutes()).isEqualTo(1);
    }

    @Test
    void expiredWindowsAreSweptAway() {
        limiter.hit("old", 5);
        clock.advance(Duration.ofMinutes(11));
        limiter.hit("new", 5);

        limiter.sweep();

        assertThat(limiter.size()).isEqualTo(1);
    }
}
