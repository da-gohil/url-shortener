package com.darshangohil.urlshortener.web.utils;

import com.darshangohil.urlshortener.domain.models.ShortUrlFilter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FilterLinksTest {

    @Test
    void noFiltersIsTheBarePath() {
        assertThat(FilterLinks.build("/my-urls", ShortUrlFilter.NONE, Map.of(), null)).isEqualTo("/my-urls");
    }

    @Test
    void onlyNonDefaultValuesAreIncludedAndEncoded() {
        var filter = ShortUrlFilter.parse("a b&c", "private", null, "clicks");

        assertThat(FilterLinks.build("/admin/links", filter, Map.of("owner", "guest"), 2))
                .isEqualTo("/admin/links?owner=guest&q=a%20b%26c&visibility=private&sort=clicks&page=2");
    }
}
