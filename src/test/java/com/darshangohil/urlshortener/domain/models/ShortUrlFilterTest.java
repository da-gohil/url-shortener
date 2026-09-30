package com.darshangohil.urlshortener.domain.models;

import com.darshangohil.urlshortener.domain.models.ShortUrlFilter.SortOrder;
import com.darshangohil.urlshortener.domain.models.ShortUrlFilter.Status;
import com.darshangohil.urlshortener.domain.models.ShortUrlFilter.Visibility;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShortUrlFilterTest {

    @Test
    void missingParametersMeanNoFilter() {
        assertThat(ShortUrlFilter.parse(null, null, null, null)).isEqualTo(ShortUrlFilter.NONE);
        assertThat(ShortUrlFilter.parse("  ", "", " ", "")).isEqualTo(ShortUrlFilter.NONE);
    }

    @Test
    void valuesAreCaseInsensitiveAndTrimmed() {
        assertThat(ShortUrlFilter.parse(" docs ", "Private", " expired", "CLICKS"))
                .isEqualTo(new ShortUrlFilter("docs", Visibility.PRIVATE, Status.EXPIRED, SortOrder.CLICKS));
    }

    @Test
    void unknownValuesFallBackToTheDefaults() {
        assertThat(ShortUrlFilter.parse(null, "sideways", "maybe", "random"))
                .isEqualTo(ShortUrlFilter.NONE);
    }

    @Test
    void sortingAloneIsCustomButDoesNotFilter() {
        var sortedOnly = ShortUrlFilter.parse(null, null, null, "oldest");

        assertThat(sortedOnly.isFiltering()).isFalse();
        assertThat(sortedOnly.isCustomised()).isTrue();
        assertThat(ShortUrlFilter.NONE.isCustomised()).isFalse();
    }

    @Test
    void anySearchOrRestrictionCountsAsFiltering() {
        assertThat(ShortUrlFilter.parse("x", null, null, null).isFiltering()).isTrue();
        assertThat(ShortUrlFilter.parse(null, "public", null, null).isFiltering()).isTrue();
        assertThat(ShortUrlFilter.parse(null, null, "active", null).isFiltering()).isTrue();
    }

    @Test
    void everySortEndsWithIdForAStableOrderAcrossPages() {
        for (SortOrder order : SortOrder.values()) {
            var orders = order.toSort().toList();
            assertThat(orders.getLast().getProperty()).as(order.name()).isEqualTo("id");
        }
    }
}
