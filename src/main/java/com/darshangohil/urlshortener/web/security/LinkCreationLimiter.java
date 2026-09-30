package com.darshangohil.urlshortener.web.security;

import com.darshangohil.urlshortener.RateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Caps how often links can be created: per IP address when signed out, per account
 * when signed in. Every attempt counts, not only successes, because each attempt may
 * make the server fetch the submitted URL. Admins are not limited.
 */
@Component
public class LinkCreationLimiter {

    private final FixedWindowRateLimiter limiter;
    private final RateLimitProperties properties;

    public LinkCreationLimiter(RateLimitProperties properties) {
        this.properties = properties;
        this.limiter = new FixedWindowRateLimiter(Duration.ofHours(1), Clock.systemUTC());
    }

    /** @param userId the signed-in user, or {@code null} for a visitor */
    public FixedWindowRateLimiter.Status attempt(Long userId, boolean admin, String clientIp) {
        if (admin) {
            return new FixedWindowRateLimiter.Status(false, Duration.ZERO);
        }
        return userId == null
                ? limiter.hit("ip:" + clientIp, properties.anonymousLinksPerHour())
                : limiter.hit("user:" + userId, properties.userLinksPerHour());
    }
}
