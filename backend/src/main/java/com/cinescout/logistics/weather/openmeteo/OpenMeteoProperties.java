package com.cinescout.logistics.weather.openmeteo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Open-Meteo settings, bound from {@code cinescout.logistics.open-meteo.*}. The free tier needs no key
 * (non-commercial use, about 10,000 calls a day); a paid plan uses different hosts, set here.
 *
 * @param forecastUrl       forecast API host
 * @param archiveUrl        historical weather API host
 * @param forecastDaysAhead how far ahead the forecast reaches (Open-Meteo: 16 days, today included)
 * @param forecastDaysBack  how far back the forecast API still serves days (up to about 90); older days
 *                          come from the archive. At least a week, the archive's own delay
 * @param timeout           whole-call budget
 */
@Validated
@ConfigurationProperties("cinescout.logistics.open-meteo")
public record OpenMeteoProperties(
        @DefaultValue("https://api.open-meteo.com") @NotBlank String forecastUrl,
        @DefaultValue("https://archive-api.open-meteo.com") @NotBlank String archiveUrl,
        @DefaultValue("15") @Min(0) @Max(15) int forecastDaysAhead,
        @DefaultValue("30") @Min(7) @Max(90) int forecastDaysBack,
        @DefaultValue("10s") @NotNull Duration timeout
) {
}
