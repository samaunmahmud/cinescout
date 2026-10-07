package com.cinescout.logistics.weather;

import java.time.ZoneId;
import java.util.List;

/**
 * Daily weather for a run of dates.
 *
 * @param zone        the location's time zone, as the provider resolved it from the coordinates; null when the
 *                    provider does not say (the days are then UTC dates)
 * @param attribution the credit for this series when it came from another provider than the client's own
 *                    {@link WeatherClient#attribution()} (a fallback); null otherwise
 */
public record WeatherSeries(ZoneId zone, List<DailyWeather> days, String attribution) {

    public WeatherSeries(ZoneId zone, List<DailyWeather> days) {
        this(zone, days, null);
    }
}
