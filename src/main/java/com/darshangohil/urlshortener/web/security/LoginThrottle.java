package com.darshangohil.urlshortener.web.security;

import com.darshangohil.urlshortener.RateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Locks out repeated failed sign-ins, per email address and per IP address, for a
 * while. Counts come from Spring Security's authentication events; the check happens
 * in {@link LoginThrottleFilter} before a password is even looked at.
 *
 * <p>Trade-off: someone who knows your email can lock you out for the lockout period
 * by failing on purpose. That's the usual price of per-account lockout, and the period
 * is short.
 */
@Component
public class LoginThrottle {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    private final FixedWindowRateLimiter limiter;
    private final RateLimitProperties properties;

    // two constructors, so Spring needs telling which one; the other takes a Clock for tests
    @Autowired
    public LoginThrottle(RateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginThrottle(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.limiter = new FixedWindowRateLimiter(Duration.ofMinutes(properties.loginLockoutMinutes()), clock);
    }

    /** Locked if either the email or the IP has used up its failures. */
    public FixedWindowRateLimiter.Status status(String email, String clientIp) {
        var byAccount = limiter.check(accountKey(email), properties.loginFailuresPerAccount());
        if (byAccount.limited()) {
            return byAccount;
        }
        return limiter.check(ipKey(clientIp), properties.loginFailuresPerIp());
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String email = event.getAuthentication().getName();
        String ip = clientIp(event.getAuthentication().getDetails());
        limiter.hit(accountKey(email), properties.loginFailuresPerAccount());
        if (ip != null) {
            limiter.hit(ipKey(ip), properties.loginFailuresPerIp());
        }
        log.info("Failed sign-in for {} from {}", email, ip);
    }

    /** A correct password clears that account's failures (not the IP's). */
    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        limiter.reset(accountKey(event.getAuthentication().getName()));
    }

    private static String accountKey(String email) {
        return "email:" + (email == null ? "" : email.strip().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private static String clientIp(Object details) {
        return details instanceof WebAuthenticationDetails web ? web.getRemoteAddress() : null;
    }
}
