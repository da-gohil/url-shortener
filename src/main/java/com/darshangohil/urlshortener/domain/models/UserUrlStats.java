package com.darshangohil.urlshortener.domain.models;

/**
 * Headline numbers for one user's links, shown above the My URLs table.
 *
 * <p>Boxed {@code Long}s because the repository builds this with a JPQL constructor
 * expression, and {@code COUNT}/{@code SUM} come back as {@code Long}.
 */
public record UserUrlStats(Long totalLinks, Long totalClicks, Long activeLinks, Long disabledLinks) {

    /**
     * Expired but not disabled. A link is exactly one of active, expired or disabled
     * (disabled wins), so the three always add up to {@link #totalLinks}.
     */
    public long expiredLinks() {
        return totalLinks - activeLinks - disabledLinks;
    }
}
