package com.darshangohil.urlshortener;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Limits on link creation and failed sign-ins. Counted in memory, so they apply per
 * app instance.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
@Validated
public record RateLimitProperties(
        /* link-creation attempts per hour from one IP address, when signed out */
        @DefaultValue("10") @Min(1) int anonymousLinksPerHour,
        /* link-creation attempts per hour per signed-in user (admins are exempt) */
        @DefaultValue("100") @Min(1) int userLinksPerHour,
        /* failed sign-ins for one email before it is locked */
        @DefaultValue("5") @Min(1) int loginFailuresPerAccount,
        /* failed sign-ins from one IP address before it is locked */
        @DefaultValue("20") @Min(1) int loginFailuresPerIp,
        /* how long both kinds of lock last, and the window failures are counted in */
        @DefaultValue("15") @Min(1) int loginLockoutMinutes) {
}
