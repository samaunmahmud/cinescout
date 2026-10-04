package com.cinescout.logistics.routing;

import com.cinescout.logistics.GeoPoint;
import reactor.core.publisher.Mono;

/**
 * Drive times between two points, behind a provider-neutral interface. Implementations fail with
 * {@link com.cinescout.logistics.LogisticsException}; they do not retry.
 */
public interface RoutingClient {

    /** The quickest drive from {@code from} to {@code to}, or an empty {@code Mono} when there is no road between them. */
    Mono<Route> drive(GeoPoint from, GeoPoint to);

    /** The credit the data's licence requires wherever a result is shown. */
    String attribution();
}
