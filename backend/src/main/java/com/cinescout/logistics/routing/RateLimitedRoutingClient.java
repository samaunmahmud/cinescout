package com.cinescout.logistics.routing;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import com.cinescout.resilience.Pacer;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Spaces out the calls to another {@link RoutingClient} across the whole app, as the public OSRM server asks. */
public class RateLimitedRoutingClient implements RoutingClient {

    private final RoutingClient delegate;
    private final Pacer pacer;

    public RateLimitedRoutingClient(RoutingClient delegate, Duration interval, Duration maxWait) {
        this.delegate = delegate;
        this.pacer = new Pacer(interval, maxWait);
    }

    @Override
    public Mono<Route> drive(GeoPoint from, GeoPoint to) {
        return pacer.pace(() -> delegate.drive(from, to),
                () -> new LogisticsException(Kind.RATE_LIMITED, "Too many routing requests are waiting; try again shortly"));
    }

    @Override
    public String attribution() {
        return delegate.attribution();
    }
}
