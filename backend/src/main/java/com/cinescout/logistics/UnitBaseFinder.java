package com.cinescout.logistics;

import com.cinescout.logistics.LogisticsReport.SectionStatus;
import com.cinescout.logistics.LogisticsReport.UnitBase;
import com.cinescout.logistics.LogisticsReport.UnitBaseSite;
import com.cinescout.logistics.places.Place;
import com.cinescout.logistics.places.PlaceKind;

import java.util.Comparator;
import java.util.List;

/**
 * Picks the likeliest unit bases from the places around a venue: the biggest first where the map says how big (a
 * tagged capacity counts as 25 m² a space, so it compares with an outline's area), then the rest by distance.
 */
public final class UnitBaseFinder {

    /** How many sites a report lists. */
    static final int LIMIT = 6;
    /** Roughly what one car space takes, aisle included. */
    static final double SQUARE_METERS_A_SPACE = 25;

    private UnitBaseFinder() {
    }

    public static UnitBase find(List<Place> places, int radiusMeters) {
        List<UnitBaseSite> sites = places.stream()
                .filter(place -> place.kind() == PlaceKind.UNIT_BASE && place.position() != null)
                .sorted(Comparator.comparing((Place place) -> size(place) == null)
                        .thenComparing(place -> size(place) == null ? 0 : -size(place))
                        .thenComparingDouble(Place::distanceMeters))
                .limit(LIMIT)
                .map(UnitBaseFinder::site)
                .toList();
        return new UnitBase(SectionStatus.OK, sites.isEmpty() ? "Nothing on the map within " + radiusMeters + " m: ask the location about parking" : null,
                radiusMeters, sites);
    }

    public static UnitBase unavailable(String message, int radiusMeters) {
        return new UnitBase(SectionStatus.UNAVAILABLE, message, radiusMeters, List.of());
    }

    /** Square metres, from the capacity if tagged, else the outline; null when neither is known. */
    static Double size(Place place) {
        Place.Size size = place.size();
        if (size == null) {
            return null;
        }
        if (size.capacity() != null) {
            return size.capacity() * SQUARE_METERS_A_SPACE;
        }
        return size.areaSquareMeters() == null || size.areaSquareMeters() < 1 ? null : size.areaSquareMeters();
    }

    private static UnitBaseSite site(Place place) {
        Place.Size size = place.size();
        String type = size == null ? null : size.type();
        String kind = type == null ? "Car park"
                : switch (type) {
                    case "rest_area" -> "Rest area";
                    case "street_side", "layby" -> "Roadside bays (lay-by)";
                    case "surface" -> "Open car park";
                    default -> "Car park";
                };
        Integer area = size == null || size.areaSquareMeters() == null ? null : (int) Math.round(size.areaSquareMeters());
        return new UnitBaseSite(place.name(), kind, size == null ? null : size.capacity(), area, (int) Math.round(place.distanceMeters()),
                place.position().latitude(), place.position().longitude());
    }
}
