package com.cinescout.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Which page of a list to return, from the {@code page} and {@code size} query parameters. Both are optional:
 * the first page, {@value #DEFAULT_SIZE} items.
 */
public record PageQuery(
        @Schema(description = "Zero-based page number", defaultValue = "0", minimum = "0")
        @Min(0) Integer page,
        @Schema(description = "Items per page", defaultValue = "" + DEFAULT_SIZE, minimum = "1", maximum = "" + MAX_SIZE)
        @Min(1) @Max(MAX_SIZE) Integer size
) {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 100;

    public static PageQuery first() {
        return new PageQuery(null, null);
    }

    /** Unsorted: each list query has its own fixed order. */
    public Pageable pageable() {
        return PageRequest.of(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }
}
