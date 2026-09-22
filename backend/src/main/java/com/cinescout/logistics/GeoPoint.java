package com.cinescout.logistics;

/**
 * A position on the earth in decimal degrees (WGS 84).
 *
 * @throws IllegalArgumentException if either coordinate is out of range or not a number
 */
public record GeoPoint(double latitude, double longitude) {

    /** Mean earth radius (IUGG), in metres. */
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    public GeoPoint {
        if (!(latitude >= -90 && latitude <= 90)) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (!(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
    }

    /** Great-circle (haversine) distance, in metres. */
    public double distanceTo(GeoPoint other) {
        double dLat = Math.toRadians(other.latitude - latitude);
        double dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /**
     * Distance to the nearest point of the segment {@code a-b}, in metres. The segment is projected onto a
     * plane around this point, which is accurate to well under a metre at the few-kilometre scale it is used for.
     */
    public double distanceToSegment(GeoPoint a, GeoPoint b) {
        double metersPerDegreeLat = Math.toRadians(EARTH_RADIUS_METERS);
        double metersPerDegreeLon = metersPerDegreeLat * Math.cos(Math.toRadians(latitude));
        double ax = (a.longitude - longitude) * metersPerDegreeLon;
        double ay = (a.latitude - latitude) * metersPerDegreeLat;
        double bx = (b.longitude - longitude) * metersPerDegreeLon;
        double by = (b.latitude - latitude) * metersPerDegreeLat;
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        // The parameter of the point on a-b closest to the origin (this point), clamped to the segment.
        double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lengthSquared));
        return Math.hypot(ax + t * dx, ay + t * dy);
    }
}
