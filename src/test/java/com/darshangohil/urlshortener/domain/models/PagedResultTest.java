package com.darshangohil.urlshortener.domain.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PagedResultTest {

    /** Renders pageLinks() the way the pager does, with "…" for a gap. */
    private static String links(int pageNumber, int totalPages) {
        PagedResult<String> page = new PagedResult<>(List.of(), 0, pageNumber, totalPages,
                pageNumber == 1, pageNumber == totalPages,
                pageNumber < totalPages, pageNumber > 1);
        return page.pageLinks().stream()
                .map(i -> i == null ? "…" : i.toString())
                .collect(Collectors.joining(" "));
    }

    @ParameterizedTest(name = "page {0} of {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "1  | 1  | 1",
            "1  | 2  | 1 2",
            "3  | 5  | 1 2 3 4 5",
            "1  | 7  | 1 2 3 … 7",
            "4  | 7  | 1 2 3 4 5 6 7",
            "7  | 7  | 1 … 5 6 7",
            "1  | 40 | 1 2 3 … 40",
            "20 | 40 | 1 … 18 19 20 21 22 … 40",
            "40 | 40 | 1 … 38 39 40",
            // a single hidden page is shown rather than replaced by "…"
            "5  | 40 | 1 2 3 4 5 6 7 … 40",
            "36 | 40 | 1 … 34 35 36 37 38 39 40",
    })
    void pageLinksShowTheEndsAndAWindowAroundTheCurrentPage(int pageNumber, int totalPages,
                                                            String expected) {
        assertThat(links(pageNumber, totalPages)).isEqualTo(expected);
    }

    @Test
    void anEmptyResultHasNoPageLinks() {
        assertThat(links(1, 0)).isEmpty();
    }

    @Test
    void theCurrentPageIsAlwaysIncluded() {
        for (int total = 1; total <= 30; total++) {
            for (int current = 1; current <= total; current++) {
                String rendered = " " + links(current, total) + " ";
                assertThat(rendered).contains(" " + current + " ");
                assertThat(Arrays.stream(rendered.trim().split(" "))
                        .filter(s -> !s.equals("…")).distinct().count())
                        .as("no duplicates for page %d of %d", current, total)
                        .isEqualTo(Arrays.stream(rendered.trim().split(" "))
                                .filter(s -> !s.equals("…")).count());
            }
        }
    }
}
