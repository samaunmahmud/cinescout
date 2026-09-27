package com.cinescout.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * One page of a list. A page past the end is empty but still says how many items and pages there are.
 *
 * @param page       zero-based
 * @param size       the page size asked for; {@code items} holds fewer on the last page
 * @param totalItems across all pages
 * @param totalPages 0 when the list is empty
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        return new PageResponse<>(page.getContent().stream().<T>map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
