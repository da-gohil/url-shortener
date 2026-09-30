package com.darshangohil.urlshortener.web.utils;

import com.darshangohil.urlshortener.domain.models.ShortUrlFilter;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Locale;
import java.util.Map;

/**
 * Builds a listing URL that carries only the filter values that differ from the
 * defaults, so links stay short and a bare path always means "no filters".
 */
public final class FilterLinks {

    private FilterLinks() {
    }

    /**
     * @param extra additional criteria to keep (e.g. {@code owner}), in order
     * @param page  appended last, or {@code null} for none
     */
    public static String build(String path, ShortUrlFilter filter, Map<String, String> extra, Integer page) {
        UriComponentsBuilder link = UriComponentsBuilder.fromPath(path);
        extra.forEach(link::queryParam);
        if (filter.query() != null) {
            link.queryParam("q", filter.query());
        }
        if (filter.visibility() != ShortUrlFilter.Visibility.ALL) {
            link.queryParam("visibility", lower(filter.visibility()));
        }
        if (filter.status() != ShortUrlFilter.Status.ALL) {
            link.queryParam("status", lower(filter.status()));
        }
        if (filter.sort() != ShortUrlFilter.SortOrder.NEWEST) {
            link.queryParam("sort", lower(filter.sort()));
        }
        if (page != null) {
            link.queryParam("page", page);
        }
        return link.encode().toUriString();
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
