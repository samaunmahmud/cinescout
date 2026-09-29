package com.cinescout.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The current time at the precision PostgreSQL keeps ({@code TIMESTAMPTZ} holds microseconds). The JVM's clock
 * reads nanoseconds on Linux (microseconds on macOS), and Postgres rounds the extra digits away, so an instant
 * stored straight from {@link Instant#now()} would read back as a different one than the app just returned.
 * Use these for every instant the app writes to the database.
 */
public final class DatabaseTime {

    private static final Duration PRECISION = Duration.of(1, ChronoUnit.MICROS);

    private DatabaseTime() {
    }

    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** The system clock in UTC, ticking in whole microseconds. */
    public static Clock clock() {
        return Clock.tick(Clock.systemUTC(), PRECISION);
    }
}
