package com.darshangohil.urlshortener.domain.models;

import org.springframework.data.domain.Page;

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
}
