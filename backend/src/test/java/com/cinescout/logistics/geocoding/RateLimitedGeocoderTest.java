package com.cinescout.logistics.geocoding;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitedGeocoderTest {

    private final List<Long> calledAt = new CopyOnWriteArrayList<>();

    private final Geocoder delegate = new Geocoder() {
        @Override
        public Mono<GeoPoint> locate(String query) {
            calledAt.add(System.nanoTime());
            return Mono.just(new GeoPoint(1, 2));
        }

        @Override
        public String attribution() {
            return "© test";
        }
    };

    @Test
    void spacesCallsOutEvenWhenTheyArriveTogether() {
        Geocoder limited = new RateLimitedGeocoder(delegate, Duration.ofMillis(200), Duration.ofSeconds(5));

        List<GeoPoint> points = Flux.range(0, 3).flatMap(i -> limited.locate("q" + i)).collectList().block();

        assertThat(points).hasSize(3);
        List<Long> sorted = calledAt.stream().sorted().toList();
        // One call per 200 ms window: the third call cannot start before the second window has passed.
        assertThat(Duration.ofNanos(sorted.get(2) - sorted.get(0))).isGreaterThanOrEqualTo(Duration.ofMillis(190));
    }

    @Test
    void aCallThatWouldWaitTooLongFailsAsRateLimitedWithoutReachingTheService() {
        Geocoder limited = new RateLimitedGeocoder(delegate, Duration.ofSeconds(10), Duration.ofMillis(50));
        limited.locate("first").block();

        assertThatThrownBy(() -> limited.locate("second").block())
                .isInstanceOfSatisfying(LogisticsException.class, e -> assertThat(e.kind()).isEqualTo(LogisticsException.Kind.RATE_LIMITED));
        assertThat(calledAt).hasSize(1);
    }

    @Test
    void keepsTheServicesAttribution() {
        assertThat(new RateLimitedGeocoder(delegate, Duration.ofSeconds(1), Duration.ofSeconds(1)).attribution()).isEqualTo("© test");
    }
}
