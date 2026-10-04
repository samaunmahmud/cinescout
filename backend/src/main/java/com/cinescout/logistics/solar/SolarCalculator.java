package com.cinescout.logistics.solar;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.solar.SceneLight.Period;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoublePredicate;

/**
 * Sunrise, sunset, golden and blue hours for a place and date, computed locally with the NOAA solar
 * position equations (accurate to about a minute between latitudes 72 N and 72 S; the midnight sun and
 * polar night are handled, just less precisely at the edges). No external service is involved.
 *
 * <p>Rather than solving for each event, the day is sampled once a minute and every stretch in which the
 * sun's elevation lies within a band is found, then its edges are refined to the second. The same method
 * gives sunrise, golden and blue hours and the scene's own windows, and it cannot miss the days on which
 * an event does not happen at all.
 */
public final class SolarCalculator {

    /** Sunrise and sunset: the sun's upper limb on the horizon, allowing for atmospheric refraction. */
    static final double HORIZON = -0.833;
    static final double GOLDEN_LOW = -4;
    static final double GOLDEN_HIGH = 6;
    static final double BLUE_LOW = -6;

    private static final long SAMPLE_SECONDS = 60;
    private static final int REFINE_STEPS = 6; // 60 s halved 6 times: under a second

    private SolarCalculator() {
    }

    /**
     * @param zone  the location's time zone: the day runs from its local midnight to the next
     * @param light the scene's light, or null if unknown (no scene windows are computed)
     */
    public static SolarDay day(LocalDate date, GeoPoint point, ZoneId zone, SceneLight light) {
        return day(date, point, zone, light, null, null);
    }

    /**
     * As {@link #day(LocalDate, GeoPoint, ZoneId, SceneLight)}, with the sun's position at the start, middle and end of
     * the scene's call-to-wrap time ({@code wrap} at or before {@code call} is the next morning); a call alone gives
     * the call time only. Without a call time, the scene's first light window is used.
     */
    public static SolarDay day(LocalDate date, GeoPoint point, ZoneId zone, SceneLight light, LocalTime call, LocalTime wrap) {
        Day day = new Day(date, point, zone);

        List<Interval> up = day.intervals(e -> e >= HORIZON);
        Interval firstUp = up.isEmpty() ? null : up.getFirst();
        Interval lastUp = up.isEmpty() ? null : up.getLast();
        // A stretch that is already open at midnight (or still open at the next) did not rise (set) today.
        OffsetDateTime sunrise = firstUp == null || firstUp.start == day.start ? null : day.local(firstUp.start);
        OffsetDateTime sunset = lastUp == null || lastUp.end == day.end ? null : day.local(lastUp.end);
        long daylightSeconds = up.stream().mapToLong(i -> i.end - i.start).sum();
        long noon = day.solarNoon();

        List<TimeWindow> sceneWindows = light == null ? List.of() : day.intervals(band(light.minElevation(), light.maxElevation()))
                .stream()
                .filter(i -> light.period() == Period.ANY
                        || (light.period() == Period.MORNING) == (i.midpoint() < noon))
                .map(day::window)
                .toList();

        return new SolarDay(date, sunrise, sunset, day.local(noon),
                (int) Math.round(daylightSeconds / 60.0),
                day.intervals(band(GOLDEN_LOW, GOLDEN_HIGH)).stream().map(day::window).toList(),
                day.intervals(band(BLUE_LOW, GOLDEN_LOW)).stream().map(day::window).toList(),
                sceneWindows,
                sunPath(date, point, zone, call, wrap, sceneWindows));
    }

    private static List<SunPosition> sunPath(LocalDate date, GeoPoint point, ZoneId zone, LocalTime call, LocalTime wrap,
                                             List<TimeWindow> sceneWindows) {
        List<OffsetDateTime> moments = new ArrayList<>();
        if (call != null) {
            OffsetDateTime start = date.atTime(call).atZone(zone).toOffsetDateTime();
            if (wrap == null) {
                moments.add(start);
            } else {
                LocalDate wrapDay = wrap.isAfter(call) ? date : date.plusDays(1);
                OffsetDateTime end = wrapDay.atTime(wrap).atZone(zone).toOffsetDateTime();
                moments.add(start);
                moments.add(start.plusSeconds(Duration.between(start, end).getSeconds() / 2).truncatedTo(ChronoUnit.MINUTES));
                moments.add(end);
            }
        } else if (!sceneWindows.isEmpty()) {
            TimeWindow window = sceneWindows.getFirst();
            moments.add(window.start());
            moments.add(window.start().plusSeconds(Duration.between(window.start(), window.end()).getSeconds() / 2).truncatedTo(ChronoUnit.MINUTES));
            moments.add(window.end());
        }
        return moments.stream().map(moment -> position(moment, point)).toList();
    }

    /** Where the sun is at {@code at}, seen from {@code point}. */
    public static SunPosition position(OffsetDateTime at, GeoPoint point) {
        double[] sun = horizontal(at.toEpochSecond(), point);
        return SunPosition.of(at, sun[0], sun[1]);
    }

    /** Elevations from {@code low} (inclusive) to {@code high} (exclusive, except at the zenith). */
    private static DoublePredicate band(double low, double high) {
        return e -> e >= low && (e < high || high >= 90);
    }

    /**
     * The sun's geometric elevation above the horizon in degrees, without refraction (NOAA's equations,
     * after Meeus, "Astronomical Algorithms").
     */
    static double elevation(long epochSecond, GeoPoint point) {
        return horizontal(epochSecond, point)[1];
    }

    /** The sun's azimuth (clockwise from true north) and geometric elevation, both in degrees. */
    static double[] horizontal(long epochSecond, GeoPoint point) {
        double julianDay = epochSecond / 86_400.0 + 2_440_587.5;
        double t = (julianDay - 2_451_545.0) / 36_525.0; // Julian centuries since J2000.0

        double meanLongitude = normalize(280.46646 + t * (36_000.76983 + t * 0.0003032));
        double meanAnomaly = 357.52911 + t * (35_999.05029 - 0.0001537 * t);
        double eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
        double m = Math.toRadians(meanAnomaly);
        double equationOfCenter = Math.sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t))
                + Math.sin(2 * m) * (0.019993 - 0.000101 * t)
                + Math.sin(3 * m) * 0.000289;
        double omega = Math.toRadians(125.04 - 1934.136 * t);
        double apparentLongitude = meanLongitude + equationOfCenter - 0.00569 - 0.00478 * Math.sin(omega);
        double meanObliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60;
        double obliquity = Math.toRadians(meanObliquity + 0.00256 * Math.cos(omega));
        double declination = Math.asin(Math.sin(obliquity) * Math.sin(Math.toRadians(apparentLongitude)));

        double y = Math.pow(Math.tan(obliquity / 2), 2);
        double l0 = Math.toRadians(meanLongitude);
        double equationOfTimeMinutes = 4 * Math.toDegrees(y * Math.sin(2 * l0)
                - 2 * eccentricity * Math.sin(m)
                + 4 * eccentricity * y * Math.sin(m) * Math.cos(2 * l0)
                - 0.5 * y * y * Math.sin(4 * l0)
                - 1.25 * eccentricity * eccentricity * Math.sin(2 * m));

        double utcMinutes = Math.floorMod(epochSecond, 86_400L) / 60.0;
        double trueSolarMinutes = floorMod(utcMinutes + equationOfTimeMinutes + 4 * point.longitude(), 1440);
        double hourAngle = Math.toRadians(trueSolarMinutes / 4 - 180);

        double latitude = Math.toRadians(point.latitude());
        double cosZenith = Math.sin(latitude) * Math.sin(declination)
                + Math.cos(latitude) * Math.cos(declination) * Math.cos(hourAngle);
        double zenith = Math.acos(Math.max(-1, Math.min(1, cosZenith)));

        // NOAA's azimuth: from the zenith, the latitude and the declination; west of the meridian when the hour angle is positive.
        double azimuth;
        double denominator = Math.cos(latitude) * Math.sin(zenith);
        if (Math.abs(denominator) > 0.001) {
            double cosine = (Math.sin(latitude) * Math.cos(zenith) - Math.sin(declination)) / denominator;
            azimuth = 180 - Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosine))));
            if (hourAngle > 0) {
                azimuth = -azimuth;
            }
        } else {
            azimuth = point.latitude() > 0 ? 180 : 0;
        }
        return new double[] {normalize(azimuth), 90 - Math.toDegrees(zenith)};
    }

    private static double normalize(double degrees) {
        return floorMod(degrees, 360);
    }

    private static double floorMod(double value, double modulus) {
        double result = value % modulus;
        return result < 0 ? result + modulus : result;
    }

    /** A stretch of the day, in epoch seconds, end exclusive. */
    private record Interval(long start, long end) {
        long midpoint() {
            return start + (end - start) / 2;
        }
    }

    /** One local day at one place, sampled once a minute. Local days may be 23 or 25 hours long. */
    private static final class Day {

        final long start;
        final long end;
        final GeoPoint point;
        final ZoneId zone;
        final long[] times;
        final double[] elevations;

        Day(LocalDate date, GeoPoint point, ZoneId zone) {
            this.start = date.atStartOfDay(zone).toEpochSecond();
            this.end = date.plusDays(1).atStartOfDay(zone).toEpochSecond();
            this.point = point;
            this.zone = zone;
            int samples = (int) ((end - start + SAMPLE_SECONDS - 1) / SAMPLE_SECONDS) + 1;
            this.times = new long[samples];
            this.elevations = new double[samples];
            for (int i = 0; i < samples; i++) {
                times[i] = Math.min(start + i * SAMPLE_SECONDS, end);
                elevations[i] = elevation(times[i], point);
            }
        }

        /** Every stretch of the day in which the elevation satisfies {@code inBand}, edges refined. */
        List<Interval> intervals(DoublePredicate inBand) {
            List<Interval> result = new ArrayList<>();
            Long openedAt = inBand.test(elevations[0]) ? start : null;
            for (int i = 1; i < times.length; i++) {
                boolean was = inBand.test(elevations[i - 1]);
                boolean is = inBand.test(elevations[i]);
                if (!was && is) {
                    openedAt = edge(times[i - 1], times[i], inBand);
                } else if (was && !is && openedAt != null) {
                    result.add(new Interval(openedAt, edge(times[i - 1], times[i], inBand)));
                    openedAt = null;
                }
            }
            if (openedAt != null) {
                result.add(new Interval(openedAt, end));
            }
            return result;
        }

        /** The second at which {@code inBand} flips between {@code from} and {@code to} (one sample apart). */
        private long edge(long from, long to, DoublePredicate inBand) {
            boolean atFrom = inBand.test(elevation(from, point));
            long low = from;
            long high = to;
            for (int step = 0; step < REFINE_STEPS && high - low > 1; step++) {
                long mid = low + (high - low) / 2;
                if (inBand.test(elevation(mid, point)) == atFrom) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            return high;
        }

        /** When the sun is highest: the best sample, refined by ternary search over the minutes around it. */
        long solarNoon() {
            int best = 0;
            for (int i = 1; i < elevations.length; i++) {
                if (elevations[i] > elevations[best]) {
                    best = i;
                }
            }
            long low = Math.max(start, times[best] - SAMPLE_SECONDS);
            long high = Math.min(end, times[best] + SAMPLE_SECONDS);
            while (high - low > 2) {
                long m1 = low + (high - low) / 3;
                long m2 = high - (high - low) / 3;
                if (elevation(m1, point) < elevation(m2, point)) {
                    low = m1;
                } else {
                    high = m2;
                }
            }
            return (low + high) / 2;
        }

        TimeWindow window(Interval interval) {
            return new TimeWindow(local(interval.start), local(interval.end));
        }

        /** Local time, rounded to the nearest minute. */
        OffsetDateTime local(long epochSecond) {
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond + 30).truncatedTo(ChronoUnit.MINUTES), zone);
        }
    }
}
