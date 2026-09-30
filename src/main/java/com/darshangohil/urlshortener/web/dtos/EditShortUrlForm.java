package com.darshangohil.urlshortener.web.dtos;

import com.darshangohil.urlshortener.domain.models.UpdateShortUrlCmd;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Locale;

/**
 * The edit-link form. {@code expiry} is one of {@code keep}, {@code never} or
 * {@code days}; anything else is treated as {@code keep}.
 */
public record EditShortUrlForm(
        Boolean isPrivate,
        String expiry,
        @Min(value = 1, message = "Enter between 1 and 365 days")
        @Max(value = 365, message = "Enter between 1 and 365 days")
        Integer expirationInDays) {

    public EditShortUrlForm() {
        this(false, "keep", null);
    }

    public UpdateShortUrlCmd.Expiry expiryChoice() {
        if (expiry == null) {
            return UpdateShortUrlCmd.Expiry.KEEP;
        }
        try {
            return UpdateShortUrlCmd.Expiry.valueOf(expiry.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UpdateShortUrlCmd.Expiry.KEEP;
        }
    }

    /** "Expire in N days" needs the N; the other choices ignore it. */
    public boolean isMissingDays() {
        return expiryChoice() == UpdateShortUrlCmd.Expiry.DAYS && expirationInDays == null;
    }

    public UpdateShortUrlCmd toCmd() {
        return new UpdateShortUrlCmd(Boolean.TRUE.equals(isPrivate), expiryChoice(), expirationInDays);
    }
}
