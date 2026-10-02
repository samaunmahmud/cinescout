package com.cinescout.scouting;

import com.cinescout.ai.SearchResult;
import com.cinescout.ai.VenueVerdict;
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

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    /** A venue's own site and, failing that, its page on a hire site: two pages are enough to find it. */
    static final int RESULTS_PER_NAMED_VENUE = 2;
    private static final int FOLLOW_UP_SEARCH_CONCURRENCY = 2;
    /** Booking sites' listing ids are long ("66876899a8dfc287ba3aeb50"); page names such as "events" are not ids. */
    private static final int MIN_LISTING_ID_LENGTH = 8;

    private final LlmClient llm;
    private final LocationSearchClient search;
    private final Guard llmGuard;
    private final Guard searchGuard;
    private final int assessmentConcurrency;
    private final int followUpVenues;

    public ScoutingPipeline(LlmClient llm, LocationSearchClient search, GuardFactory guards, ScoutingProperties props) {
        this.llm = llm;
        this.search = search;
        this.assessmentConcurrency = props.assessmentConcurrency();
        this.followUpVenues = props.followUpVenues();
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
     * Finds venues for the requirements in the area and assesses each against them. Search results that turn out to
     * be directories name venues of their own: up to {@link ScoutingProperties#followUpVenues()} of those are looked
     * up by name (one small search each) and assessed too, since for many settings the web's first answers are
     * directories rather than venues. A failed follow-up search only costs that venue.
     *
     * @param maxResults how many venues to look for, see {@link LocationSearchRequest#MAX_RESULTS}
     */
    public Mono<ScoutingOutcome> scout(SceneRequirements requirements, String area, int maxResults) {
        return searchGuard.call(() -> search.search(new LocationSearchRequest(requirements, area, maxResults)))
                .flatMap(hits -> assessAll(requirements, area, hits))
                .flatMap(first -> followUp(requirements, area, first)
                        .map(more -> {
                            List<Assessed> all = new ArrayList<>(first);
                            all.addAll(more);
                            return all;
                        }))
                .flatMap(this::toOutcome);
    }

    private Mono<List<Assessed>> assessAll(SceneRequirements requirements, String area, List<SearchResult> hits) {
        // flatMapSequential: assessments run concurrently but come back in search-rank order.
        return Flux.fromIterable(hits)
                .flatMapSequential(hit -> assess(requirements, area, hit)
                                .map(Assessed::new)
                                .onErrorResume(LlmException.class, error -> Mono.just(new Assessed(error))),
                        assessmentConcurrency)
                .collectList();
    }

    /** Looks up and assesses the venues the first round's directories named and the first round did not find. */
    private Mono<List<Assessed>> followUp(SceneRequirements requirements, String area, List<Assessed> first) {
        List<String> names = namedByDirectories(first, followUpVenues);
        if (names.isEmpty()) {
            return Mono.just(List.of());
        }
        log.debug("Scouting looks up {} venue(s) named by directories: {}", names.size(), names);
        Set<String> seenUrls = new HashSet<>();
        first.stream().filter(a -> a.venue() != null).forEach(a -> seenUrls.add(a.venue().source().url()));
        return Flux.fromIterable(names)
                .flatMapSequential(name -> searchGuard.call(() -> search.findVenue(name, area, RESULTS_PER_NAMED_VENUE))
                                .onErrorResume(SearchException.class, error -> {
                                    log.warn("Scouting could not look up a venue a directory named ({}): {}", error.kind(), error.getMessage());
                                    return Mono.just(List.of());
                                }),
                        FOLLOW_UP_SEARCH_CONCURRENCY)
                .flatMapIterable(hits -> hits)
                .filter(hit -> seenUrls.add(hit.url()))
                .collectList()
                .flatMap(hits -> assessAll(requirements, area, hits));
    }

    /**
     * The names the directories among {@code assessed} put forward, best-ranked directory first, without the venues
     * that were found in their own right and without repeats; at most {@code limit}.
     */
    static List<String> namedByDirectories(List<Assessed> assessed, int limit) {
        List<String> known = new ArrayList<>();
        for (Assessed a : assessed) {
            if (a.venue() != null && a.venue().assessment().singleVenue() && a.venue().assessment().venueName() != null) {
                known.add(a.venue().assessment().venueName());
            }
        }
        List<String> names = new ArrayList<>();
        for (Assessed a : assessed) {
            if (a.venue() == null || a.venue().assessment().singleVenue()) {
                continue;
            }
            for (String name : a.venue().assessment().listedVenues()) {
                if (names.size() < limit && known.stream().noneMatch(k -> VenueNames.sameVenue(k, name))) {
                    known.add(name);
                    names.add(name);
                }
            }
        }
        return names;
    }

    private Mono<ScoutedVenue> assess(SceneRequirements requirements, String area, SearchResult hit) {
        return llmGuard.call(() -> llm.generate(ScoutingPrompts.ASSESSMENT_SYSTEM,
                        ScoutingPrompts.assessmentUser(requirements, area, hit), VenueVerdict.class))
                .map(verdict -> new ScoutedVenue(hit, verdict.toAssessment(requirements)));
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
        // Scored 0 is unusable by the assessment's own scale (e.g. in another city): not worth a place on the list.
        List<ScoutedVenue> usable = venues.stream().filter(v -> v.assessment().fitScore() > 0).toList();
        int unsuitable = venues.size() - usable.size();
        if (notVenues > 0 || unsuitable > 0) {
            log.info("Scouting dropped {} of {} search result(s) that were not about one venue and {} unsuitable venue(s)",
                    notVenues, assessed.size(), unsuitable);
        }
        return Mono.just(new ScoutingOutcome(usable, failures.size(), notVenues, unsuitable));
    }

    /**
     * One result per venue: a venue's own site often comes back as several pages (home, menu, events), each assessed
     * separately, and a venue a directory named may be found on its own site and a booking site. Results whose venue
     * names match ({@link VenueNames#sameVenue}), whose street addresses match ({@link VenueNames#sameStreetAddress}),
     * or that are copies of one listing on a booking site ({@link #sameListing}) are one venue, kept at its best
     * score; if that page gave no address, the first of the others that did lends it.
     */
    static List<ScoutedVenue> sameVenueOnce(List<ScoutedVenue> bestFirst) {
        List<ScoutedVenue> kept = new ArrayList<>();
        for (ScoutedVenue venue : bestFirst) {
            int at = indexOfSameVenue(kept, venue);
            if (at < 0) {
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

    private static int indexOfSameVenue(List<ScoutedVenue> venues, ScoutedVenue venue) {
        for (int i = 0; i < venues.size(); i++) {
            ScoutedVenue other = venues.get(i);
            if (VenueNames.sameVenue(other.assessment().venueName(), venue.assessment().venueName())
                    || VenueNames.sameStreetAddress(other.assessment().address(), venue.assessment().address())
                    || sameListing(other.source().url(), venue.source().url())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether two URLs are the same listing on a booking site under different paths, e.g. a site's US and Australian
     * copies ({@code /pages/listings/6687...} and {@code /au/pages/listings/6687...}): the same host and the same
     * last path segment, when that segment is a long id rather than a word such as "events".
     */
    static boolean sameListing(String a, String b) {
        try {
            URI first = URI.create(a);
            URI second = URI.create(b);
            String id = lastSegment(first.getPath());
            return first.getHost() != null && hostOf(first).equals(hostOf(second))
                    && id.length() >= MIN_LISTING_ID_LENGTH && id.chars().anyMatch(Character::isDigit)
                    && id.equals(lastSegment(second.getPath()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String hostOf(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static String lastSegment(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }

    /** One venue's assessment attempt: exactly one of the two is set. */
    record Assessed(ScoutedVenue venue, LlmException failure) {
        Assessed(ScoutedVenue venue) {
            this(venue, null);
        }

        Assessed(LlmException failure) {
            this(null, failure);
        }
    }
}
