package com.cinescout.logistics.geocoding.nominatim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Nominatim settings, bound from {@code cinescout.logistics.nominatim.*}. The public instance is free and
 * keyless under a usage policy (at most one request a second, an identifying User-Agent, no bulk jobs);
 * geocoding here happens once per venue at a user's request, well inside it. Heavier use should point
 * {@code base-url} at a private instance or a commercial Nominatim host.
 *
 * @param baseUrl Nominatim host
 * @param timeout whole-call budget
 */
@Validated
@ConfigurationProperties("cinescout.logistics.nominatim")
public record NominatimProperties(
        @DefaultValue("https://nominatim.openstreetmap.org") @NotBlank String baseUrl,
        @DefaultValue("10s") @NotNull Duration timeout
) {
}
