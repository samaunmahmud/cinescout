package com.cinescout.jobs;

import com.cinescout.logistics.weather.DailyWeather;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A shoot day whose forecast crosses a project's thresholds: the chance of rain at or over the rain threshold, or the
 * day's highest wind speed at or over the wind threshold. A forecast that gives no chance of rain, only an amount (the
 * fallback provider outside the Nordic countries), counts as rain from {@link #RAIN_MM_WITHOUT_CHANCE}.
 *
 * @param reasons RAIN, WIND or both, in that order
 * @param rainMm  the day's forecast rain, where given
 */
record WeatherBreach(LocalDate day, List<String> reasons, Integer rainChance, Double rainMm, Double windKmh, Double gustKmh) {

    /** The same amount the logistics report calls "rain likely". */
    static final double RAIN_MM_WITHOUT_CHANCE = 5;

    /** The days from {@code from} to {@code to} (inclusive) of a forecast that cross either threshold, in date order. */
    static List<WeatherBreach> find(List<DailyWeather> forecast, LocalDate from, LocalDate to, int rainThreshold, int windThreshold) {
        List<WeatherBreach> breaches = new ArrayList<>();
        for (DailyWeather day : forecast) {
            if (day.date().isBefore(from) || day.date().isAfter(to)) {
                continue;
            }
            List<String> reasons = new ArrayList<>();
            Integer chance = day.precipitationProbabilityPercent();
            if (chance != null ? chance >= rainThreshold
                    : day.precipitationMm() != null && day.precipitationMm() >= RAIN_MM_WITHOUT_CHANCE) {
                reasons.add("RAIN");
            }
            if (day.windSpeedMaxKmh() != null && day.windSpeedMaxKmh() >= windThreshold) {
                reasons.add("WIND");
            }
            if (!reasons.isEmpty()) {
                breaches.add(new WeatherBreach(day.date(), List.copyOf(reasons), chance, day.precipitationMm(), day.windSpeedMaxKmh(),
                        day.windGustsMaxKmh()));
            }
        }
        breaches.sort(Comparator.comparing(WeatherBreach::day));
        return breaches;
    }
}
