package com.cinescout.logistics.solar;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The light on one shoot day at one place, in the location's local time, to the minute.
 *
 * @param sunrise         null when the sun does not rise that day (polar night or midnight sun)
 * @param sunset          null when the sun does not set that day
 * @param daylightMinutes how long the sun is up: 1440 under the midnight sun, 0 in polar night
 * @param goldenHours     sun between 4 degrees below and 6 degrees above the horizon: warm, soft, low light
 * @param blueHours       sun between 6 and 4 degrees below the horizon: deep blue ambient light, no direct sun
 * @param sceneWindows    the windows that match the scene's time of day; empty if it is unknown or never occurs
 */
public record SolarDay(
        LocalDate date,
        OffsetDateTime sunrise,
        OffsetDateTime sunset,
        OffsetDateTime solarNoon,
        int daylightMinutes,
        List<TimeWindow> goldenHours,
        List<TimeWindow> blueHours,
        List<TimeWindow> sceneWindows
) {
}
