package com.cinescout.scouting;

import com.cinescout.ai.LocationAssessment;
import com.cinescout.ai.SearchResult;
import com.cinescout.domain.BookingFriction;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.geocoding.Geocoder;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class VenuePlacerTest {

    private static final String AREA = "Brooklyn, New York";

    private final List<String> queries = new CopyOnWriteArrayList<>();

    private Geocoder geocoder(Function<String, Mono<GeoPoint>> answer) {
        return new Geocoder() {
            @Override
            public Mono<GeoPoint> locate(String query) {
                queries.add(query);
                return answer.apply(query);
            }

            @Override
            public String attribution() {
                return "test";
            }
        };
    }

    private static ScoutedVenue venue(String key, String venueName, String address) {
        return new ScoutedVenue(new SearchResult("Title " + key, "https://" + key + ".example/", null, "parallel"),
                new LocationAssessment(70, "ok", BookingFriction.PUBLIC, null, List.of(), venueName, address));
    }

    @Test
    void looksVenuesUpByAddressThenByNameThenByPageTitleInTheArea() {
        assertThat(VenuePlacer.query(venue("a", "Alpha", "1 First St, Brooklyn"), AREA)).isEqualTo("1 First St, Brooklyn");
        assertThat(VenuePlacer.query(venue("b", "Beta", null), AREA)).isEqualTo("Beta, Brooklyn, New York");
        assertThat(VenuePlacer.query(venue("c", null, null), AREA)).isEqualTo("Title c, Brooklyn, New York");
    }

    @Test
    void returnsTheCoordinatesFoundByUrlAndSkipsVenuesNotFoundOrFailing() {
        GeoPoint alpha = new GeoPoint(40.7, -73.9);
        VenuePlacer placer = new VenuePlacer(geocoder(query -> switch (query) {
            case "1 First St" -> Mono.just(alpha);
            case "2 Second St" -> Mono.error(new LogisticsException(LogisticsException.Kind.UNAVAILABLE, "down"));
            default -> Mono.empty();
        }), Duration.ofSeconds(5));

        Map<String, GeoPoint> placed = placer.place(List.of(
                venue("a", null, "1 First St"), venue("b", null, "2 Second St"), venue("c", null, "3 Third St")), AREA).block();

        assertThat(placed).containsExactly(Map.entry("https://a.example/", alpha));
        assertThat(queries).containsExactly("1 First St", "2 Second St", "3 Third St");
    }

    @Test
    void looksVenuesUpOneAtATime() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger mostAtOnce = new AtomicInteger();
        VenuePlacer placer = new VenuePlacer(geocoder(query -> Mono.fromRunnable(() -> mostAtOnce.accumulateAndGet(inFlight.incrementAndGet(), Math::max))
                .then(Mono.delay(Duration.ofMillis(30)))
                .then(Mono.fromCallable(() -> {
                    inFlight.decrementAndGet();
                    return new GeoPoint(1, 1);
                }))), Duration.ofSeconds(5));

        assertThat(placer.place(List.of(venue("a", null, "1"), venue("b", null, "2"), venue("c", null, "3")), AREA).block()).hasSize(3);
        assertThat(mostAtOnce).hasValue(1);
    }

    @Test
    void stopsWhenTheBudgetRunsOutAndKeepsWhatItFound() {
        VenuePlacer placer = new VenuePlacer(geocoder(query -> Mono.delay(Duration.ofMillis(300)).map(tick -> new GeoPoint(1, 1))),
                Duration.ofMillis(750));

        Map<String, GeoPoint> placed = placer.place(List.of(venue("a", null, "1"), venue("b", null, "2"), venue("c", null, "3")), AREA)
                .block(Duration.ofSeconds(5));

        assertThat(placed).containsOnlyKeys("https://a.example/", "https://b.example/");
    }

    @Test
    void aZeroBudgetTurnsPlacementOff() {
        VenuePlacer placer = new VenuePlacer(geocoder(query -> Mono.just(new GeoPoint(1, 1))), Duration.ZERO);

        assertThat(placer.place(List.of(venue("a", null, "1")), AREA).block()).isEmpty();
        assertThat(queries).isEmpty();
    }
}
