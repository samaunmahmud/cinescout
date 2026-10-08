package com.cinescout.logistics.geocoding;

import com.cinescout.domain.AdminArea;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import com.cinescout.resilience.Pacer;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Spaces out the calls to another {@link Geocoder}: never two less than {@code interval} apart, across the whole app
 * (logistics and scouting share it). A call waits its turn without blocking a thread; one that would wait
 * longer than {@code maxWait} fails as {@link Kind#RATE_LIMITED} instead.
 */
public class RateLimitedGeocoder implements Geocoder {

    private final Geocoder delegate;
    private final Pacer pacer;

    public RateLimitedGeocoder(Geocoder delegate, Duration interval, Duration maxWait) {
        this.delegate = delegate;
        this.pacer = new Pacer(interval, maxWait);
    }

    @Override
    public Mono<GeoPoint> locate(String query) {
        return pacer.pace(() -> delegate.locate(query),
                () -> new LogisticsException(Kind.RATE_LIMITED, "Too many geocoding requests are waiting; try again shortly"));
    }

    @Override
    public Mono<AdminArea> areaAt(GeoPoint point) {
        return pacer.pace(() -> delegate.areaAt(point),
                () -> new LogisticsException(Kind.RATE_LIMITED, "Too many geocoding requests are waiting; try again shortly"));
    }

    @Override
    public Mono<String> placeAt(GeoPoint point, String languages) {
        return pacer.pace(() -> delegate.placeAt(point, languages),
                () -> new LogisticsException(Kind.RATE_LIMITED, "Too many geocoding requests are waiting; try again shortly"));
    }

    @Override
    public String attribution() {
        return delegate.attribution();
    }
}
