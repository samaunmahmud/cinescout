package com.cinescout.logistics.weather;

import java.time.ZoneId;
import java.util.List;

/**
 * Daily weather for a run of dates.
 *
 * @param zone the location's time zone, as the provider resolved it from the coordinates
 */
public record WeatherSeries(ZoneId zone, List<DailyWeather> days) {
}
