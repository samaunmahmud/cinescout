package com.cinescout.logistics;

import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.logistics.solar.SceneLight;
import com.cinescout.logistics.weather.DailyWeather;

import java.util.ArrayList;
import java.util.List;

/** Reads a day's weather the way a first AD would: what it means for the shoot, in plain words. */
final class WeatherAdvisor {

    static final int RAIN_PROBABILITY_PERCENT = 50;
    static final double RAIN_MM = 5;
    static final double STRONG_GUSTS_KMH = 50;
    static final double MICROPHONE_GUSTS_KMH = 30;
    static final double HOT_C = 32;
    static final double FREEZING_C = 0;
    static final int OVERCAST_PERCENT = 80;

    private WeatherAdvisor() {
    }

    /** The WMO weather code in words; null if there is no code. */
    static String summary(Integer code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75, 77 -> "Snow";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Unknown (WMO code " + code + ")";
        };
    }

    /**
     * @param light       the scene's light, or null; overcast only matters to a scene that wants the low sun
     * @param sensitivity the scene's acoustic sensitivity, or null; wind on the microphones only matters to a
     *                    scene whose sound is recorded clean
     */
    static List<String> warnings(DailyWeather day, SceneLight light, AcousticSensitivity sensitivity) {
        List<String> warnings = new ArrayList<>();
        Integer code = day.weatherCode();
        if (code != null && code >= 95) {
            warnings.add("Thunderstorms: lightning risk, keep crew off rigs, cranes and rooftops");
        }
        if (atLeast(day.precipitationProbabilityPercent(), RAIN_PROBABILITY_PERCENT) || atLeast(day.precipitationMm(), RAIN_MM)) {
            warnings.add("Rain likely: plan cover for cast, crew and equipment");
        }
        if (code != null && (code >= 71 && code <= 77 || code == 85 || code == 86)) {
            warnings.add("Snow: allow extra travel time and watch continuity");
        }
        if (code != null && (code == 45 || code == 48)) {
            warnings.add("Fog: low visibility and a flat, diffused look");
        }
        if (atLeast(day.windGustsMaxKmh(), STRONG_GUSTS_KMH)) {
            warnings.add("Strong gusts: secure flags, frames and overheads; drones may be grounded");
        } else if (sensitivity == AcousticSensitivity.HIGH && atLeast(day.windGustsMaxKmh(), MICROPHONE_GUSTS_KMH)) {
            warnings.add("Gusty: expect wind noise on exterior microphones");
        }
        if (atLeast(day.temperatureMaxC(), HOT_C)) {
            warnings.add("Heat: shade, water and rest breaks for cast and crew");
        }
        if (day.temperatureMinC() != null && day.temperatureMinC() <= FREEZING_C) {
            warnings.add("Freezing: keep batteries warm and protect cast between takes");
        }
        if ((light == SceneLight.DAWN || light == SceneLight.DUSK || light == SceneLight.GOLDEN_HOUR)
                && atLeast(day.cloudCoverPercent(), OVERCAST_PERCENT)) {
            warnings.add("Mostly overcast: little direct sun at golden hour");
        }
        return List.copyOf(warnings);
    }

    private static boolean atLeast(Number value, double threshold) {
        return value != null && value.doubleValue() >= threshold;
    }
}
