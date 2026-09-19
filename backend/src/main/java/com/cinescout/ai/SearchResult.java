package com.cinescout.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * One grounded web hit returned by a {@code LocationSearchClient}. It is
 * provider-neutral: each client maps its own response into this shape, so the
 * pipeline (and {@code locations.source_provider}) never depends on one vendor.
 *
 * @param title    page title, used as the venue's working name
 * @param url      the page the venue was found on; stored as {@code source_url}
 * @param excerpt  the passage that grounds the result; stored as {@code source_excerpt}
 * @param provider which search backend produced it, e.g. {@code "parallel"}
 */
public record SearchResult(
        @NotBlank String title,
        // http(s) only: this URL is stored and later rendered as a link.
        @NotBlank @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String url,
        String excerpt,
        @NotBlank String provider
) {
}
