package com.cinescout.scouting;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.geocoding.Geocoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Finds newly scouted venues on the map, so they can be shown there straight away. Best effort: a venue
 * that cannot be found, or a geocoder that is down, only leaves that venue without coordinates (its
 * logistics geocode it again later, or the user sets them).
 *
 * <p>The geocoder is rate limited (the public Nominatim allows one request a second), so venues are
 * looked up one at a time and the whole step is cut off after {@code budget}; venues not reached by
 * then are saved without coordinates rather than holding up the run.
 */
public class VenuePlacer {

    private static final Logger log = LoggerFactory.getLogger(VenuePlacer.class);

    private final Geocoder geocoder;
    private final Duration budget;

    public VenuePlacer(Geocoder geocoder, Duration budget) {
        this.geocoder = geocoder;
        this.budget = budget;
    }

    /** The coordinates found, by the venue's source URL; venues not found are left out. */
    public Mono<Map<String, GeoPoint>> place(List<ScoutedVenue> venues, String area) {
        if (venues.isEmpty() || budget.isZero()) {
            return Mono.just(Map.of());
        }
        return Flux.fromIterable(venues)
                .concatMap(venue -> geocoder.locate(query(venue, area))
                        .map(point -> Tuples.of(venue.source().url(), point))
                        .onErrorResume(error -> {
                            log.info("Could not place scouted venue {}: {}", venue.source().url(), error.toString());
                            return Mono.empty();
                        }))
                .take(budget)
                .collectMap(found -> found.getT1(), found -> found.getT2());
    }

    /**
     * Where an address is, for a scouting run's base point; empty when it cannot be found (or the lookup fails or
     * takes longer than the budget), in which case the run goes on without a radius.
     */
    public Mono<GeoPoint> locate(String address) {
        if (address == null || address.isBlank()) {
            return Mono.empty();
        }
        return geocoder.locate(address)
                .timeout(budget.isZero() ? Duration.ofSeconds(10) : budget)
                .onErrorResume(error -> {
                    log.info("Could not find a scouting base point at {}: {}", address, error.toString());
                    return Mono.empty();
                });
    }

    /** The address the page gives; failing that the venue's name, in the search area so it is found in the right city. */
    static String query(ScoutedVenue venue, String area) {
        String address = venue.assessment().address();
        if (address != null) {
            return address;
        }
        String name = venue.assessment().venueName() != null ? venue.assessment().venueName() : venue.source().title();
        return name + ", " + area;
    }
}
