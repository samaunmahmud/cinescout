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

    /**
     * Finds the pages of one named venue in an area: its own website first, else its page on a venue-hire site.
     * Used for venues a directory page named, so the venue itself can be assessed.
     *
     * @param name       the venue's name as the directory gave it, e.g. {@code "Bar Blondeau, Wythe Hotel"}
     * @param area       free-text place, as in {@link LocationSearchRequest#area()}
     * @param maxResults upper bound on hits returned, 1 to {@value LocationSearchRequest#MAX_RESULTS}
     * @return at most {@code maxResults} validated hits, best first; failures arrive as {@link SearchException}
     */
    Mono<List<SearchResult>> findVenue(String name, String area, int maxResults);
}
