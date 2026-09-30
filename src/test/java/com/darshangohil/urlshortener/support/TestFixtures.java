package com.darshangohil.urlshortener.support;

import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import com.darshangohil.urlshortener.domain.entities.User;
import com.darshangohil.urlshortener.domain.models.PagedResult;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.SecurityUser;
import com.darshangohil.urlshortener.domain.models.ShortUrlDto;
import com.darshangohil.urlshortener.domain.models.UserDto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/** Small builders so the tests read as scenarios rather than object graphs. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static User user(Long id, String name, Role role) {
        var user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(' ', '.') + "@example.com");
        user.setPassword("{noop}irrelevant");
        user.setRole(role);
        user.setCreatedAt(OffsetDateTime.now());
        return user;
    }

    public static SecurityUser principal(Long id, String name, Role role) {
        return new SecurityUser(user(id, name, role));
    }

    public static ShortUrl shortUrl(Long id, String shortKey, boolean isPrivate, User owner) {
        var shortUrl = new ShortUrl();
        shortUrl.setId(id);
        shortUrl.setShortKey(shortKey);
        shortUrl.setOriginalUrl("https://example.com/" + shortKey);
        shortUrl.setIsPrivate(isPrivate);
        shortUrl.setCreatedBy(owner);
        shortUrl.setClickCount(0L);
        shortUrl.setCreatedAt(Instant.now());
        return shortUrl;
    }

    public static ShortUrlDto dto(Long id, String shortKey, boolean isPrivate, UserDto owner) {
        return new ShortUrlDto(id, shortKey, "https://example.com/" + shortKey, isPrivate,
                null, owner, 7L, Instant.parse("2026-01-02T03:04:05Z"));
    }

    /** A single-page result, which is what most of the web tests need. */
    public static <T> PagedResult<T> onePage(List<T> data) {
        return new PagedResult<>(data, data.size(), 1, 1, true, true, false, false);
    }

    /** A result that reports more pages, so the pager fragment actually renders. */
    public static <T> PagedResult<T> pageOneOf(List<T> data, int totalPages) {
        return new PagedResult<>(data, (long) data.size() * totalPages, 1, totalPages,
                true, false, true, false);
    }
}
