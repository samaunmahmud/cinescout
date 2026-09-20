package com.cinescout.search;

import com.cinescout.ai.SearchResult;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Provider-neutral grounded web search for filming venues. Each implementation (currently
 * Parallel) maps its vendor's response into {@link SearchResult}, so the pipeline and
 * {@code locations.source_provider} never depend on one vendor.
 */
public interface LocationSearchClient {

    /**
     * Finds real venues matching the requirements in the requested area.
     *
     * @return at most {@code request.maxResults()} distinct, validated hits, best first; empty
     *         when nothing matched. Failures arrive as {@link SearchException}.
     */
    Mono<List<SearchResult>> search(LocationSearchRequest request);
}
