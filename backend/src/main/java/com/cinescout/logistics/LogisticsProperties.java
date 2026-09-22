package com.cinescout.logistics;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings shared by the logistics module, bound from {@code cinescout.logistics.*}. Each provider has
 * its own block beneath (open-meteo, overpass, nominatim).
 *
 * @param userAgent sent with every provider request. The OpenStreetMap services' usage policies require
 *                  one that identifies the application and a way to reach its operator; a deployment
 *                  should put its own contact URL or email here
 * @param maxDays   the longest shoot window worked out in one report; longer windows are cut short
 */
@Validated
@ConfigurationProperties("cinescout.logistics")
public record LogisticsProperties(
        @DefaultValue("CineScout/0.1 (+https://github.com/samaunmahmud/cinescout)") @NotBlank String userAgent,
        @DefaultValue("14") @Min(1) @Max(31) int maxDays
) {
}
