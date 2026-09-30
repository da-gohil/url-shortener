package com.darshangohil.urlshortener.domain.models;

import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * View-friendly slice of a {@link Page}, so the web layer never has to touch
 * Spring Data types directly.
 *
 * <p>{@code pageNumber} is 1-based, matching the {@code ?page=} request parameter.
 */
public record PagedResult<T>(
        List<T> data,
        long totalElements,
        int pageNumber,
        int totalPages,
        boolean isFirst,
        boolean isLast,
        boolean hasNext,
        boolean hasPrevious
) {

    /** How many page links to show either side of the current page. */
    private static final int WINDOW = 2;

    public static <E, T> PagedResult<T> from(Page<E> page, Function<E, T> mapper) {
        return new PagedResult<>(
                page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious()
        );
    }

    /**
     * True when the requested page is past the end of a non-empty result, e.g.
     * {@code ?page=999}, or a page emptied by deletions. An empty result is never
     * out of range, so callers do not redirect-loop on it.
     */
    public boolean isBeyondLastPage() {
        return totalPages > 0 && pageNumber > totalPages;
    }

    /**
     * Page numbers for the pager: always the first and last page plus {@link #WINDOW}
     * pages either side of the current one, e.g. {@code 1 … 4 5 6 7 8 … 40}.
     *
     * <p>A {@code null} entry marks a gap, rendered as an ellipsis. A gap is only
     * used when it would hide two or more pages; hiding a single page behind "…"
     * takes as much room as just showing its number.
     */
    public List<Integer> pageLinks() {
        if (totalPages <= 0) {
            return List.of();
        }
        int from = Math.max(1, pageNumber - WINDOW);
        int to = Math.min(totalPages, pageNumber + WINDOW);

        List<Integer> links = new ArrayList<>();
        links.add(1);
        if (from > 3) {
            links.add(null);
        } else {
            for (int i = 2; i < from; i++) {
                links.add(i);
            }
        }
        for (int i = Math.max(from, 2); i <= Math.min(to, totalPages - 1); i++) {
            links.add(i);
        }
        if (to < totalPages - 2) {
            links.add(null);
        } else {
            for (int i = Math.max(to + 1, 2); i < totalPages; i++) {
                links.add(i);
            }
        }
        if (totalPages > 1) {
            links.add(totalPages);
        }
        return links;
    }
}
