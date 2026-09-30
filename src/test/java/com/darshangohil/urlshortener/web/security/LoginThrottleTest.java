package com.darshangohil.urlshortener.web.security;

import com.darshangohil.urlshortener.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTest {

    private final FixedWindowRateLimiterTest.TestClock clock = new FixedWindowRateLimiterTest.TestClock();
    // 3 failures per account, 5 per IP, locked for 15 minutes
    private final LoginThrottle throttle = new LoginThrottle(new RateLimitProperties(10, 100, 3, 5, 15), clock);

    @Test
    void anAccountIsLockedAfterItsFailures() {
        fail("john@example.com", "10.0.0.1", 3);

        var status = throttle.status("John@Example.com", "10.0.0.99");
        assertThat(status.limited()).isTrue();
        assertThat(status.retryAfterMinutes()).isEqualTo(15);
        // someone else signing in from the same place is fine
        assertThat(throttle.status("jane@example.com", "10.0.0.1").limited()).isFalse();
    }

    @Test
    void anIpIsLockedAfterFailuresAcrossManyAccounts() {
        for (int i = 0; i < 5; i++) {
            fail("user" + i + "@example.com", "10.0.0.1", 1);
        }

        assertThat(throttle.status("fresh@example.com", "10.0.0.1").limited()).isTrue();
        assertThat(throttle.status("fresh@example.com", "10.0.0.2").limited()).isFalse();
    }

    @Test
    void theLockLiftsAfterTheLockoutPeriod() {
        fail("john@example.com", "10.0.0.1", 3);

        clock.advance(Duration.ofMinutes(15));

        assertThat(throttle.status("john@example.com", "10.0.0.1").limited()).isFalse();
    }

    @Test
    void aSuccessfulSignInClearsTheAccountsFailures() {
        fail("john@example.com", "10.0.0.1", 2);

        throttle.onSuccess(new AuthenticationSuccessEvent(
                UsernamePasswordAuthenticationToken.authenticated("john@example.com", null, java.util.List.of())));
        fail("john@example.com", "10.0.0.1", 2);

        assertThat(throttle.status("john@example.com", "10.0.0.1").limited()).isFalse();
    }

    private void fail(String email, String ip, int times) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        var attempt = UsernamePasswordAuthenticationToken.unauthenticated(email, "wrong");
        attempt.setDetails(new WebAuthenticationDetails(request));
        for (int i = 0; i < times; i++) {
            throttle.onFailure(new AuthenticationFailureBadCredentialsEvent(attempt, new BadCredentialsException("bad")));
        }
    }
}
