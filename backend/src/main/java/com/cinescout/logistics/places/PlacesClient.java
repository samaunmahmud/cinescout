package com.cinescout.logistics.places;

import com.cinescout.logistics.GeoPoint;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Finds the places around a location that matter to a shoot, behind a provider-neutral interface.
 * Implementations look within each {@link PlaceKind}'s radius and fail with
 * {@link com.cinescout.logistics.LogisticsException}; they do not retry.
 */
public interface PlacesClient {

    /** Every place of every {@link PlaceKind} within that kind's radius of {@code point}, in no particular order. */
    Mono<List<Place>> around(GeoPoint point);

    /** The credit the data's licence requires wherever it is shown. */
    String attribution();
}
