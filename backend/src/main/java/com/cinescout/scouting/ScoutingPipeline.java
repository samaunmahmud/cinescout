package com.cinescout.scouting;

import com.cinescout.ai.LocationAssessment;
import com.cinescout.ai.SearchResult;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.domain.VenueNames;
import com.cinescout.llm.LlmClient;
import com.cinescout.llm.LlmException;
import com.cinescout.llm.LlmGuards;
import com.cinescout.resilience.Guard;
import com.cinescout.resilience.GuardFactory;
import com.cinescout.search.LocationSearchClient;
import com.cinescout.search.LocationSearchRequest;
import com.cinescout.search.SearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The AI half of scouting, free of persistence: extract a scene's requirements, search for real
 * venues, and have the model assess each one. Every LLM and search call goes through a
 * {@link Guard} (retry with back-off inside a circuit breaker), so callers see either a result
 * or a classified {@link LlmException} / {@link SearchException}, never a hung or hammering call.
 *
 * <p>A failed assessment costs one venue, not the run: the others are returned and the shortfall
 * is reported in {@link ScoutingOutcome#unassessed()}. Only when <em>every</em> assessment fails is
 * the failure raised, since that means an outage rather than a bad answer.
 */
public class ScoutingPipeline {

    private static final Logger log = LoggerFactory.getLogger(ScoutingPipeline.class);

    private final LlmClient llm;
    private final LocationSearchClient search;
    private final Guard llmGuard;
    private final Guard searchGuard;
    private final int assessmentConcurrency;

    public ScoutingPipeline(LlmClient llm, LocationSearchClient search, GuardFactory guards, ScoutingProperties props) {
        this.llm = llm;
        this.search = search;
        this.assessmentConcurrency = props.assessmentConcurrency();
        this.llmGuard = LlmGuards.create(guards);
        this.searchGuard = guards.create("search",
                error -> error instanceof SearchException e && e.isRetryable(),
                error -> error instanceof SearchException e
                        && (e.kind() == SearchException.Kind.UNAVAILABLE || e.kind() == SearchException.Kind.RATE_LIMITED),
                open -> new SearchException(SearchException.Kind.UNAVAILABLE,
                        "The search circuit breaker is open; the call was not made", open));
    }

    /**
     * Extracts the physical filming requirements from a scene's text. An answer without a setting
     * type (or otherwise failing {@link SceneRequirements}' constraints) is treated as unusable and
     * retried; if it stays unusable the {@code INVALID_OUTPUT} failure is emitted.
     */
    public Mono<SceneRequirements> extractRequirements(String sceneText) {
        return llmGuard.call(() -> llm.generate(
                ScoutingPrompts.EXTRACTION_SYSTEM, ScoutingPrompts.extractionUser(sceneText), SceneRequirements.class));
    }

    /**
     * Finds venues for the requirements in the area and assesses each against them.
     *
     * @param maxResults how many venues to look for, see {@link LocationSearchRequest#MAX_RESULTS}
     */
    public Mono<ScoutingOutcome> scout(SceneRequirements requirements, String area, int maxResults) {
        return searchGuard.call(() -> search.search(new LocationSearchRequest(requirements, area, maxResults)))
                .flatMap(hits -> assessAll(requirements, area, hits));
    }

    private Mono<ScoutingOutcome> assessAll(SceneRequirements requirements, String area, List<SearchResult> hits) {
        // flatMapSequential: assessments run concurrently but come back in search-rank order.
        return Flux.fromIterable(hits)
                .flatMapSequential(hit -> assess(requirements, area, hit)
                                .map(Assessed::new)
                                .onErrorResume(LlmException.class, error -> Mono.just(new Assessed(error))),
                        assessmentConcurrency)
                .collectList()
                .flatMap(this::toOutcome);
    }

    private Mono<ScoutedVenue> assess(SceneRequirements requirements, String area, SearchResult hit) {
        return llmGuard.call(() -> llm.generate(ScoutingPrompts.ASSESSMENT_SYSTEM,
                        ScoutingPrompts.assessmentUser(requirements, area, hit), LocationAssessment.class))
                .map(assessment -> new ScoutedVenue(hit, assessment));
    }

    private Mono<ScoutingOutcome> toOutcome(List<Assessed> assessed) {
        List<ScoutedVenue> venues = new ArrayList<>();
        List<LlmException> failures = new ArrayList<>();
        int notVenues = 0;
        for (Assessed a : assessed) {
            if (a.venue() != null && !a.venue().assessment().singleVenue()) {
                notVenues++; // a directory or an article: nothing anyone can book or put on the map
                log.debug("Not one venue: {} ({})", a.venue().source().title(), a.venue().source().url());
            } else if (a.venue() != null) {
                venues.add(a.venue());
            } else {
                failures.add(a.failure());
            }
        }
        if (venues.isEmpty() && notVenues == 0 && !failures.isEmpty()) {
            // Nothing could be assessed: that is an outage or a misconfiguration, not a quiet search.
            return Mono.error(failures.get(0));
        }
        if (!failures.isEmpty()) {
            log.warn("Scouting dropped {} of {} venue(s) the model could not assess: {}", failures.size(), assessed.size(),
                    failures.stream().map(f -> f.kind().name()).collect(Collectors.joining(", ")));
        }
        // List.sort is stable, so equal scores keep the search's own relevance order.
        venues.sort(Comparator.comparing((ScoutedVenue v) -> v.assessment().fitScore()).reversed());
        venues = sameVenueOnce(venues);
        if (notVenues > 0) {
            log.info("Scouting dropped {} of {} search result(s) that were not about one venue", notVenues, assessed.size());
        }
        return Mono.just(new ScoutingOutcome(venues, failures.size(), notVenues));
    }

    /**
     * One result per venue: a venue's own site often comes back as several pages (home, menu, events), each assessed
     * separately. Results the model gave the same venue name (see {@link VenueNames}) are one venue, kept at its best
     * score; if that page gave no address, the first of the others that did lends it. Results without a venue name
     * are all kept, since nothing says they are the same place.
     */
    static List<ScoutedVenue> sameVenueOnce(List<ScoutedVenue> bestFirst) {
        List<ScoutedVenue> kept = new ArrayList<>();
        Map<String, Integer> keptAt = new HashMap<>();
        for (ScoutedVenue venue : bestFirst) {
            String name = VenueNames.key(venue.assessment().venueName());
            Integer at = name == null ? null : keptAt.get(name);
            if (at == null) {
                if (name != null) {
                    keptAt.put(name, kept.size());
                }
                kept.add(venue);
            } else if (kept.get(at).assessment().address() == null && venue.assessment().address() != null) {
                kept.set(at, kept.get(at).withAddress(venue.assessment().address()));
            }
        }
        if (kept.size() < bestFirst.size()) {
            log.debug("Scouting merged {} result(s) that were pages of a venue already found", bestFirst.size() - kept.size());
        }
        return kept;
    }

    /** One venue's assessment attempt: exactly one of the two is set. */
    private record Assessed(ScoutedVenue venue, LlmException failure) {
        Assessed(ScoutedVenue venue) {
            this(venue, null);
        }

        Assessed(LlmException failure) {
            this(null, failure);
        }
    }
}
