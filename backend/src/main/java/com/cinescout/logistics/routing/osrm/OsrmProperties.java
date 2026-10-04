package com.cinescout.logistics.routing.osrm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * OSRM settings, bound from {@code cinescout.logistics.osrm.*}. The public demo server is free and keyless under a
 * usage policy (at most one request a second, an identifying User-Agent, no heavy use); CineScout asks only for the
 * legs of a shoot day, a few at a time, and keeps every answer. Heavier use should point {@code base-url} at an own
 * OSRM instance.
 *
 * @param minInterval the least time between two requests, across the app
 * @param maxWait     how long a request may wait for its turn before it fails as rate limited
 */
@Validated
@ConfigurationProperties("cinescout.logistics.osrm")
public record OsrmProperties(
        @DefaultValue("https://router.project-osrm.org") @NotBlank String baseUrl,
        @DefaultValue("10s") @NotNull Duration timeout,
        @DefaultValue("1100ms") @NotNull Duration minInterval,
        @DefaultValue("30s") @NotNull Duration maxWait
) {
}
