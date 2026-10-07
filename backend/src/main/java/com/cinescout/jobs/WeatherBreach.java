package com.cinescout.jobs;

import com.cinescout.logistics.weather.DailyWeather;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A shoot day whose forecast crosses a project's thresholds: the chance of rain at or over the rain threshold, or the
 * day's highest wind speed at or over the wind threshold.
 *
 * @param reasons RAIN, WIND or both, in that order
 */
record WeatherBreach(LocalDate day, List<String> reasons, Integer rainChance, Double windKmh, Double gustKmh) {

    /** The days from {@code from} to {@code to} (inclusive) of a forecast that cross either threshold, in date order. */
    static List<WeatherBreach> find(List<DailyWeather> forecast, LocalDate from, LocalDate to, int rainThreshold, int windThreshold) {
        List<WeatherBreach> breaches = new ArrayList<>();
        for (DailyWeather day : forecast) {
            if (day.date().isBefore(from) || day.date().isAfter(to)) {
                continue;
            }
            List<String> reasons = new ArrayList<>();
            if (day.precipitationProbabilityPercent() != null && day.precipitationProbabilityPercent() >= rainThreshold) {
                reasons.add("RAIN");
            }
            if (day.windSpeedMaxKmh() != null && day.windSpeedMaxKmh() >= windThreshold) {
                reasons.add("WIND");
            }
            if (!reasons.isEmpty()) {
                breaches.add(new WeatherBreach(day.date(), List.copyOf(reasons), day.precipitationProbabilityPercent(), day.windSpeedMaxKmh(),
                        day.windGustsMaxKmh()));
            }
        }
        breaches.sort(Comparator.comparing(WeatherBreach::day));
        return breaches;
    }
}
