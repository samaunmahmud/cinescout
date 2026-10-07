package com.cinescout.logistics.weather.metno;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * MET Norway settings, bound from {@code cinescout.logistics.met-norway.*}. The Locationforecast API is free and keyless;
 * its terms ask for a User-Agent naming the app and a contact ({@code cinescout.logistics.user-agent}).
 *
 * @param enabled whether forecasts fall back to MET Norway when Open-Meteo turns them away
 * @param baseUrl the API host
 * @param timeout whole-call budget
 */
@Validated
@ConfigurationProperties("cinescout.logistics.met-norway")
public record MetNorwayProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://api.met.no") @NotBlank String baseUrl,
        @DefaultValue("10s") @NotNull Duration timeout
) {
}
