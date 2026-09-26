package com.cinescout.logistics.geocoding;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Spaces out the calls to another {@link Geocoder}: at most one per {@code interval}, across the whole app
 * (logistics and scouting share it). A call waits its turn without blocking a thread; one that would wait
 * longer than {@code maxWait} fails as {@link Kind#RATE_LIMITED} instead.
 */
public class RateLimitedGeocoder implements Geocoder {

    private final Geocoder delegate;
    private final RateLimiter limiter;

    public RateLimitedGeocoder(Geocoder delegate, Duration interval, Duration maxWait) {
        this.delegate = delegate;
        this.limiter = RateLimiter.of("geocoder", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(interval)
                .timeoutDuration(maxWait)
                .build());
    }

    @Override
    public Mono<GeoPoint> locate(String query) {
        return Mono.defer(() -> delegate.locate(query))
                .transformDeferred(RateLimiterOperator.of(limiter))
                .onErrorMap(RequestNotPermitted.class,
                        e -> new LogisticsException(Kind.RATE_LIMITED, "Too many geocoding requests are waiting; try again shortly", e));
    }

    @Override
    public String attribution() {
        return delegate.attribution();
    }
}
