package com.darshangohil.urlshortener.domain.models;

/**
 * Whose links an admin listing shows: everyone's, only guest-created ones, or one
 * user's. Parsed leniently from {@code ?owner=} ({@code guest} or a user id).
 */
public record OwnerFilter(Kind kind, Long userId) {

    public enum Kind { ANYONE, GUESTS, USER }

    public static final OwnerFilter ANYONE = new OwnerFilter(Kind.ANYONE, null);
    public static final OwnerFilter GUESTS = new OwnerFilter(Kind.GUESTS, null);

    public static OwnerFilter user(Long userId) {
        return new OwnerFilter(Kind.USER, userId);
    }

    public static OwnerFilter parse(String value) {
        if (value == null || value.isBlank()) {
            return ANYONE;
        }
        if ("guest".equalsIgnoreCase(value.strip())) {
            return GUESTS;
        }
        try {
            return user(Long.valueOf(value.strip()));
        } catch (NumberFormatException e) {
            return ANYONE;
        }
    }

    /** The {@code ?owner=} value that reproduces this filter, or {@code null} for anyone. */
    public String toParam() {
        return switch (kind) {
            case ANYONE -> null;
            case GUESTS -> "guest";
            case USER -> userId.toString();
        };
    }
}
