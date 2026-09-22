package com.cinescout.logistics.weather;

import java.time.LocalDate;

/**
 * One day's weather at one place. Any value the provider did not report is null; the precipitation
 * probability in particular only exists for forecasts.
 *
 * @param weatherCode WMO weather interpretation code (0 clear sky ... 99 thunderstorm with hail)
 */
public record DailyWeather(
        LocalDate date,
        Integer weatherCode,
        Double temperatureMaxC,
        Double temperatureMinC,
        Double precipitationMm,
        Integer precipitationProbabilityPercent,
        Double windSpeedMaxKmh,
        Double windGustsMaxKmh,
        Integer cloudCoverPercent
) {
}
