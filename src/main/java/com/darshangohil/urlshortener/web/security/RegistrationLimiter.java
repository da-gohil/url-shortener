package com.darshangohil.urlshortener.web.security;

import com.darshangohil.urlshortener.RateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Caps how many accounts one IP address can try to create per hour. Only attempts that
 * pass form validation count, so typos don't use the allowance up; what's left are new
 * accounts and probes for emails that are already registered.
 */
@Component
public class RegistrationLimiter {

    private final FixedWindowRateLimiter limiter;
    private final RateLimitProperties properties;

    public RegistrationLimiter(RateLimitProperties properties) {
        this.properties = properties;
        this.limiter = new FixedWindowRateLimiter(Duration.ofHours(1), Clock.systemUTC());
    }

    public FixedWindowRateLimiter.Status attempt(String clientIp) {
        return limiter.hit("ip:" + clientIp, properties.registrationsPerIpPerHour());
    }
}
