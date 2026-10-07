package com.cinescout.logistics.places;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * A primary {@link PlacesClient} with a second server behind it. Public map servers refuse addresses that are over
 * their limits, and a free host's address is shared, so a lookup the primary turns away quickly (refused, rate limited,
 * busy) is asked of the fallback. One that failed slowly (a timeout) is not: that would only double the wait.
 */
public class FallbackPlacesClient implements PlacesClient {

    private static final Logger log = LoggerFactory.getLogger(FallbackPlacesClient.class);

    private final PlacesClient primary;
    private final PlacesClient fallback;
    private final Duration quickFailure;

    /** @param quickFailure how soon a failure has to come for the fallback to be asked */
    public FallbackPlacesClient(PlacesClient primary, PlacesClient fallback, Duration quickFailure) {
        this.primary = primary;
        this.fallback = fallback;
        this.quickFailure = quickFailure;
    }

    @Override
    public Mono<List<Place>> around(GeoPoint point) {
        return Mono.defer(() -> {
            long started = System.nanoTime();
            return primary.around(point).onErrorResume(
                    error -> error instanceof LogisticsException e && e.isRetryable()
                            && Duration.ofNanos(System.nanoTime() - started).compareTo(quickFailure) < 0,
                    error -> {
                        log.info("Places lookup falls back to the second server: {}", error.getMessage());
                        return fallback.around(point).onErrorResume(LogisticsException.class, second -> Mono.error(error));
                    });
        });
    }

    @Override
    public int unitBaseRadiusMeters() {
        return primary.unitBaseRadiusMeters();
    }

    @Override
    public String attribution() {
        return primary.attribution();
    }
}
