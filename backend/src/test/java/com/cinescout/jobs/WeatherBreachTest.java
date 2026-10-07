package com.cinescout.jobs;

import com.cinescout.logistics.weather.DailyWeather;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherBreachTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 2);

    private static DailyWeather day(LocalDate date, Integer rainChance, Double wind) {
        return new DailyWeather(date, 61, 14.0, 8.0, 3.0, rainChance, wind, wind == null ? null : wind + 20, 90);
    }

    @Test
    void aDayCrossesAThresholdWhenItReachesItAndOnlyShootDaysCount() {
        List<WeatherBreach> found = WeatherBreach.find(List.of(
                day(MON.plusDays(2), 10, 40.0),        // wind exactly at the threshold
                day(MON, 60, 12.0),                    // rain exactly at the threshold
                day(MON.plusDays(1), 59, 39.9),        // just under both
                day(MON.plusDays(3), 95, 80.0)),       // after the shoot
                MON, MON.plusDays(2), 60, 40);

        assertThat(found).extracting(WeatherBreach::day).containsExactly(MON, MON.plusDays(2));
        assertThat(found.get(0).reasons()).containsExactly("RAIN");
        assertThat(found.get(1).reasons()).containsExactly("WIND");
        assertThat(found.get(1).gustKmh()).isEqualTo(60.0);
    }

    @Test
    void bothReasonsOnOneDayAndMissingValuesNeverAlert() {
        assertThat(WeatherBreach.find(List.of(day(MON, 90, 50.0)), MON, MON, 60, 40).getFirst().reasons()).containsExactly("RAIN", "WIND");
        assertThat(WeatherBreach.find(List.of(day(MON, null, null)), MON, MON, 1, 5)).isEmpty();
    }

    @Test
    void aForecastWithAnAmountButNoChanceOfRainCountsFromFiveMillimetres() {
        DailyWeather wet = new DailyWeather(MON, 63, 14.0, 8.0, 5.0, null, 10.0, 20.0, 90);
        DailyWeather damp = new DailyWeather(MON.plusDays(1), 61, 14.0, 8.0, 4.9, null, 10.0, 20.0, 90);

        List<WeatherBreach> found = WeatherBreach.find(List.of(wet, damp), MON, MON.plusDays(1), 60, 40);

        assertThat(found).extracting(WeatherBreach::day).containsExactly(MON);
        assertThat(found.getFirst().rainChance()).isNull();
        assertThat(found.getFirst().rainMm()).isEqualTo(5.0);
    }
}
