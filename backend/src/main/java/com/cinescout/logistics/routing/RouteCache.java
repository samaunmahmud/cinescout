package com.cinescout.logistics.routing;

import com.cinescout.logistics.GeoPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Drives already asked for, by their ends rounded to about a metre. Blocking (JDBC): call it inside a
 * {@code BlockingTransactions} block. An entry older than {@link #TTL} counts as missing.
 */
@Component
public class RouteCache {

    static final Duration TTL = Duration.ofDays(30);

    private final JdbcTemplate jdbc;

    RouteCache(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Empty when not asked yet (or long ago); a present empty inner Optional when the router found no road.
     */
    public Optional<Optional<Route>> find(GeoPoint from, GeoPoint to) {
        List<Optional<Route>> rows = jdbc.query("""
                        SELECT found, distance_m, duration_s FROM route_cache
                        WHERE from_lat = ? AND from_lng = ? AND to_lat = ? AND to_lng = ? AND fetched_at > now() - make_interval(days => ?)""",
                (rs, i) -> rs.getBoolean("found") ? Optional.of(new Route(rs.getDouble("distance_m"), rs.getDouble("duration_s"))) : Optional.empty(),
                round(from.latitude()), round(from.longitude()), round(to.latitude()), round(to.longitude()), (int) TTL.toDays());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void put(GeoPoint from, GeoPoint to, Route route) {
        jdbc.update("""
                        INSERT INTO route_cache (from_lat, from_lng, to_lat, to_lng, found, distance_m, duration_s, fetched_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, now())
                        ON CONFLICT (from_lat, from_lng, to_lat, to_lng)
                        DO UPDATE SET found = EXCLUDED.found, distance_m = EXCLUDED.distance_m, duration_s = EXCLUDED.duration_s, fetched_at = now()""",
                round(from.latitude()), round(from.longitude()), round(to.latitude()), round(to.longitude()),
                route != null, route == null ? null : route.distanceMeters(), route == null ? null : route.durationSeconds());
    }

    private static BigDecimal round(double degrees) {
        return BigDecimal.valueOf(degrees).setScale(5, RoundingMode.HALF_UP);
    }
}
