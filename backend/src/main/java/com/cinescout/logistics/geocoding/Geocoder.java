package com.cinescout.logistics.geocoding;

import com.cinescout.logistics.GeoPoint;
import reactor.core.publisher.Mono;

/**
 * Turns a free-text address or place name into coordinates, behind a provider-neutral interface.
 * Implementations fail with {@link com.cinescout.logistics.LogisticsException}; they do not retry.
 */
public interface Geocoder {

    /** The best match for {@code query}, or an empty {@code Mono} if nothing matches. */
    Mono<GeoPoint> locate(String query);

    /** The credit the data's licence requires wherever a result is shown. */
    String attribution();
}
