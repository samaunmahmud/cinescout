package com.cinescout.logistics.geocoding;

import com.cinescout.domain.AdminArea;
import com.cinescout.logistics.GeoPoint;
import reactor.core.publisher.Mono;

/**
 * Turns a free-text address or place name into coordinates, behind a provider-neutral interface.
 * Implementations fail with {@link com.cinescout.logistics.LogisticsException}; they do not retry.
 */
public interface Geocoder {

    /** The best match for {@code query}, or an empty {@code Mono} if nothing matches. */
    Mono<GeoPoint> locate(String query);

    /**
     * The local authority area {@code point} falls in (a London borough, say), or an empty {@code Mono} when the
     * provider does not know or cannot tell.
     */
    default Mono<AdminArea> areaAt(GeoPoint point) {
        return Mono.empty();
    }

    /** The credit the data's licence requires wherever a result is shown. */
    String attribution();
}
