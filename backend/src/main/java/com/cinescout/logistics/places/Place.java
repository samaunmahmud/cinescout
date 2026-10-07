package com.cinescout.logistics.places;

import com.cinescout.logistics.GeoPoint;

/**
 * A place near a location.
 *
 * @param name           null when the map has no name for it
 * @param position       a representative point (the centre of an area); null for lines such as a railway,
 *                       where only the distance to the nearest stretch is meaningful
 * @param distanceMeters from the location to the place (the centre of an area), or to the nearest point of a line
 * @param size           how big a car park or lay-by is, where the map says; null for other places
 */
public record Place(PlaceKind kind, String name, GeoPoint position, double distanceMeters, Size size) {

    public Place(PlaceKind kind, String name, GeoPoint position, double distanceMeters) {
        this(kind, name, position, distanceMeters, null);
    }

    /**
     * @param capacity          spaces, from its capacity tag; null when not tagged
     * @param areaSquareMeters  its outline's bounding box, roughly; null for a point
     * @param type              what kind of parking: "surface", "street_side" (bays along the road, often a lay-by),
     *                          "rest_area", or null when not tagged
     */
    public record Size(Integer capacity, Double areaSquareMeters, String type) {
    }
}
