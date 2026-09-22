package com.cinescout.logistics;

import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.logistics.solar.SceneLight;
import com.cinescout.logistics.weather.DailyWeather;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherAdvisorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    private static DailyWeather day(Integer code, Double max, Double min, Double rainMm, Integer rainChance,
                                    Double gusts, Integer cloud) {
        return new DailyWeather(DAY, code, max, min, rainMm, rainChance, 10.0, gusts, cloud);
    }

    private static DailyWeather calm() {
        return day(1, 20.0, 12.0, 0.0, 5, 15.0, 20);
    }

    @Test
    void aCalmDayHasNoWarnings() {
        assertThat(WeatherAdvisor.warnings(calm(), SceneLight.DUSK, AcousticSensitivity.HIGH)).isEmpty();
    }

    @Test
    void rainIsLikelyByChanceOrByAmount() {
        assertThat(WeatherAdvisor.warnings(day(3, 20.0, 12.0, 0.0, 50, 15.0, 90), null, null))
                .containsExactly("Rain likely: plan cover for cast, crew and equipment");
        // Recorded (past-year) days have no probability, only an amount.
        assertThat(WeatherAdvisor.warnings(day(63, 20.0, 12.0, 5.0, null, 15.0, 90), null, null))
                .containsExactly("Rain likely: plan cover for cast, crew and equipment");
        assertThat(WeatherAdvisor.warnings(day(51, 20.0, 12.0, 4.9, 49, 15.0, 90), null, null)).isEmpty();
    }

    @Test
    void stormsSnowAndFogAreNamed() {
        assertThat(WeatherAdvisor.warnings(day(95, 25.0, 18.0, 0.0, 0, 15.0, 50), null, null))
                .anyMatch(w -> w.startsWith("Thunderstorms"));
        assertThat(WeatherAdvisor.warnings(day(73, 1.0, -3.0, 0.0, 0, 15.0, 50), null, null))
                .anyMatch(w -> w.startsWith("Snow")).anyMatch(w -> w.startsWith("Freezing"));
        assertThat(WeatherAdvisor.warnings(day(45, 10.0, 5.0, 0.0, 0, 15.0, 100), null, null))
                .containsExactly("Fog: low visibility and a flat, diffused look");
    }

    @Test
    void strongGustsMatterToEveryoneAndLighterOnesOnlyToCleanSound() {
        assertThat(WeatherAdvisor.warnings(day(1, 20.0, 12.0, 0.0, 0, 50.0, 10), null, AcousticSensitivity.LOW))
                .containsExactly("Strong gusts: secure flags, frames and overheads; drones may be grounded");
        assertThat(WeatherAdvisor.warnings(day(1, 20.0, 12.0, 0.0, 0, 30.0, 10), null, AcousticSensitivity.HIGH))
                .containsExactly("Gusty: expect wind noise on exterior microphones");
        assertThat(WeatherAdvisor.warnings(day(1, 20.0, 12.0, 0.0, 0, 30.0, 10), null, AcousticSensitivity.MEDIUM)).isEmpty();
    }

    @Test
    void heatAndFrost() {
        assertThat(WeatherAdvisor.warnings(day(0, 32.0, 22.0, 0.0, 0, 10.0, 0), null, null))
                .containsExactly("Heat: shade, water and rest breaks for cast and crew");
        assertThat(WeatherAdvisor.warnings(day(0, 5.0, 0.0, 0.0, 0, 10.0, 0), null, null))
                .containsExactly("Freezing: keep batteries warm and protect cast between takes");
    }

    @Test
    void overcastOnlyMattersToASceneThatWantsTheLowSun() {
        DailyWeather overcast = day(3, 20.0, 12.0, 0.0, 0, 10.0, 80);
        String warning = "Mostly overcast: little direct sun at golden hour";

        assertThat(WeatherAdvisor.warnings(overcast, SceneLight.DUSK, null)).containsExactly(warning);
        assertThat(WeatherAdvisor.warnings(overcast, SceneLight.DAWN, null)).containsExactly(warning);
        assertThat(WeatherAdvisor.warnings(overcast, SceneLight.GOLDEN_HOUR, null)).containsExactly(warning);
        assertThat(WeatherAdvisor.warnings(overcast, SceneLight.NIGHT, null)).isEmpty();
        assertThat(WeatherAdvisor.warnings(overcast, null, null)).isEmpty();
    }

    @Test
    void missingValuesRaiseNothing() {
        assertThat(WeatherAdvisor.warnings(new DailyWeather(DAY, null, null, null, null, null, null, null, null),
                SceneLight.DUSK, AcousticSensitivity.HIGH)).isEmpty();
    }

    @Test
    void weatherCodesInWords() {
        assertThat(WeatherAdvisor.summary(0)).isEqualTo("Clear sky");
        assertThat(WeatherAdvisor.summary(3)).isEqualTo("Overcast");
        assertThat(WeatherAdvisor.summary(81)).isEqualTo("Rain showers");
        assertThat(WeatherAdvisor.summary(99)).isEqualTo("Thunderstorm with hail");
        assertThat(WeatherAdvisor.summary(42)).isEqualTo("Unknown (WMO code 42)");
        assertThat(WeatherAdvisor.summary(null)).isNull();
    }
}
