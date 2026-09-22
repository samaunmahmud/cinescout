package com.cinescout.logistics.places;

import com.cinescout.logistics.GeoPoint;

/**
 * A place near a location.
 *
 * @param name           null when the map has no name for it
 * @param position       a representative point (the centre of an area); null for lines such as a railway,
 *                       where only the distance to the nearest stretch is meaningful
 * @param distanceMeters from the location to the place (the centre of an area), or to the nearest point of a line
 */
public record Place(PlaceKind kind, String name, GeoPoint position, double distanceMeters) {
}
