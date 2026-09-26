package com.cinescout.video;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Finds videos about a place, behind a provider-neutral interface. Implementations fail with
 * {@link VideoException}; they do not retry.
 */
public interface VideoSearchClient {

    /** Up to {@code maxResults} embeddable videos matching {@code query}, best match first. */
    Mono<List<Video>> search(String query, int maxResults);
}
