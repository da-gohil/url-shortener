package com.darshangohil.urlshortener.domain.models;

/**
 * Changes an owner may make to an existing short URL. The destination and the short
 * key are deliberately not editable: a link someone already shared should keep going
 * where it went.
 *
 * @param expirationInDays only read when {@code expiry} is {@link Expiry#DAYS}
 */
public record UpdateShortUrlCmd(boolean isPrivate, Expiry expiry, Integer expirationInDays) {

    public enum Expiry {
        /** Leave the current expiry alone. */
        KEEP,
        /** Remove the expiry; the link never expires. */
        NEVER,
        /** Expire {@code expirationInDays} days from now. */
        DAYS
    }
}
