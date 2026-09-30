package com.darshangohil.urlshortener.domain.repository;

import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import com.darshangohil.urlshortener.domain.models.ShortUrlFilter;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Criteria building blocks for filtered short-URL listings. */
public final class ShortUrlSpecifications {

    private ShortUrlSpecifications() {
    }

    public static Specification<ShortUrl> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("createdBy").get("id"), userId);
    }

    /** Every restriction the filter asks for; its sort order is applied separately. */
    public static Specification<ShortUrl> matching(ShortUrlFilter filter, Instant now) {
        List<Specification<ShortUrl>> parts = new ArrayList<>();
        if (filter.query() != null) {
            parts.add(search(filter.query()));
        }
        switch (filter.visibility()) {
            case PUBLIC -> parts.add((root, query, cb) -> cb.isFalse(root.get("isPrivate")));
            case PRIVATE -> parts.add((root, query, cb) -> cb.isTrue(root.get("isPrivate")));
            case ALL -> { }
        }
        switch (filter.status()) {
            case ACTIVE -> parts.add((root, query, cb) -> cb.or(
                    cb.isNull(root.get("expiresAt")),
                    cb.greaterThan(root.get("expiresAt"), now)));
            case EXPIRED -> parts.add((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("expiresAt"), now));
            case ALL -> { }
        }
        return Specification.allOf(parts);
    }

    /** Case-insensitive substring match on the short key or the destination URL. */
    private static Specification<ShortUrl> search(String text) {
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("shortKey")), pattern, '\\'),
                cb.like(cb.lower(root.get("originalUrl")), pattern, '\\'));
    }

    /** So a search for "50%" or "my_page" matches those characters literally. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
