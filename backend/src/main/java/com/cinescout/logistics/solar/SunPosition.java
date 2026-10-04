package com.cinescout.logistics.solar;

import java.time.OffsetDateTime;

/**
 * Where the sun is at one moment, seen from the venue.
 *
 * @param at        the moment, in the location's local time
 * @param azimuth   compass bearing of the sun in degrees, clockwise from true north (90 east, 180 south)
 * @param elevation degrees above the horizon (negative below it), without refraction
 * @param compass   the bearing as one of eight compass points ("SW")
 * @param text      the position in words: "Sun from SW (225°), 18° high at 16:00"
 */
public record SunPosition(OffsetDateTime at, double azimuth, double elevation, String compass, String text) {

    private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    static SunPosition of(OffsetDateTime at, double azimuth, double elevation) {
        double bearing = Math.round(azimuth * 10) / 10.0;
        double height = Math.round(elevation * 10) / 10.0;
        String compass = compass(bearing);
        String clock = "%02d:%02d".formatted(at.getHour(), at.getMinute());
        long degrees = Math.round(bearing) % 360;
        long high = Math.round(height);
        String text = high >= 0
                ? "Sun from %s (%d°), %d° high at %s".formatted(compass, degrees, high, clock)
                : "Sun %d° below the horizon (%s, %d°) at %s".formatted(-high, compass, degrees, clock);
        return new SunPosition(at, bearing, height, compass, text);
    }

    /** One of eight compass points, each covering 45 degrees centred on it. */
    static String compass(double azimuth) {
        int index = (int) Math.floor(((azimuth % 360 + 360) % 360 + 22.5) / 45) % 8;
        return POINTS[index];
    }
}
