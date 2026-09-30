package com.darshangohil.urlshortener.domain.models;

import java.io.Serializable;
import java.time.Instant;

/**
 * DTO for {@link com.darshangohil.urlshortener.domain.entities.ShortUrl}
 */
public record ShortUrlDto(Long id, String shortKey, String originalUrl, Boolean isPrivate, Instant expiresAt,
                          UserDto createdBy, Long clickCount, Instant createdAt, boolean disabled)
        implements Serializable {

    /** A link that has not been disabled by an admin. */
    public ShortUrlDto(Long id, String shortKey, String originalUrl, Boolean isPrivate, Instant expiresAt,
                       UserDto createdBy, Long clickCount, Instant createdAt) {
        this(id, shortKey, originalUrl, isPrivate, expiresAt, createdBy, clickCount, createdAt, false);
    }

    /** Past its expiry: the short link no longer redirects. */
    public boolean isExpired() {
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }
}