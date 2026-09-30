package com.darshangohil.urlshortener.domain.models;

import org.springframework.data.domain.Sort;

import java.util.Locale;

/**
 * Search, filter and sort choices for a listing of short URLs.
 *
 * <p>Built from request parameters with {@link #parse}, which never fails: a blank or
 * unrecognised value falls back to the default, so a hand-edited URL shows a sensible
 * listing instead of an error page.
 */
public record ShortUrlFilter(String query, Visibility visibility, Status status, SortOrder sort) {

    public enum Visibility { ALL, PUBLIC, PRIVATE }

    public enum Status { ALL, ACTIVE, EXPIRED }

    public enum SortOrder {
        NEWEST(Sort.by(Sort.Order.desc("createdAt"))),
        OLDEST(Sort.by(Sort.Order.asc("createdAt"))),
        CLICKS(Sort.by(Sort.Order.desc("clickCount"), Sort.Order.desc("createdAt"))),
        // ascending expiry; PostgreSQL sorts NULLs (never expires) last for ASC
        EXPIRY(Sort.by(Sort.Order.asc("expiresAt"), Sort.Order.desc("createdAt")));

        private final Sort sort;

        SortOrder(Sort sort) {
            // id last, so rows that tie on everything else keep a stable order across pages
            this.sort = sort.and(Sort.by(Sort.Order.desc("id")));
        }

        public Sort toSort() {
            return sort;
        }
    }

    public static final ShortUrlFilter NONE =
            new ShortUrlFilter(null, Visibility.ALL, Status.ALL, SortOrder.NEWEST);

    public ShortUrlFilter {
        query = query == null || query.isBlank() ? null : query.strip();
    }

    public static ShortUrlFilter parse(String query, String visibility, String status, String sort) {
        return new ShortUrlFilter(query,
                parseEnum(Visibility.class, visibility, Visibility.ALL),
                parseEnum(Status.class, status, Status.ALL),
                parseEnum(SortOrder.class, sort, SortOrder.NEWEST));
    }

    /** True when the listing is narrowed down, i.e. it may be hiding some links. */
    public boolean isFiltering() {
        return query != null || visibility != Visibility.ALL || status != Status.ALL;
    }

    /** True when anything, including the sort order, differs from the defaults. */
    public boolean isCustomised() {
        return !this.equals(NONE);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
