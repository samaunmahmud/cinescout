package com.cinescout.logistics.weather;

import com.cinescout.logistics.weather.WeatherPlan.Basis;
import com.cinescout.logistics.weather.WeatherPlan.Entry;
import com.cinescout.logistics.weather.WeatherPlan.Range;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherPlanTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);

    private static WeatherPlan plan(LocalDate... dates) {
        return WeatherPlan.of(List.of(dates), TODAY, 15, 30);
    }

    @Test
    void upcomingDaysWithinTheHorizonAreForecast() {
        WeatherPlan plan = plan(TODAY, TODAY.plusDays(15));

        assertThat(plan.entries()).containsExactly(
                new Entry(TODAY, Basis.FORECAST, TODAY, true),
                new Entry(TODAY.plusDays(15), Basis.FORECAST, TODAY.plusDays(15), true));
        assertThat(plan.forecastRange()).contains(new Range(TODAY, TODAY.plusDays(15)));
        assertThat(plan.historyRange()).isEmpty();
    }

    @Test
    void daysBeyondTheHorizonGetTheSameDateLastYear() {
        LocalDate shoot = LocalDate.of(2026, 12, 24);

        WeatherPlan plan = plan(shoot);

        assertThat(plan.entries()).containsExactly(new Entry(shoot, Basis.PAST_YEAR, LocalDate.of(2025, 12, 24), false));
        assertThat(plan.historyRange()).contains(new Range(LocalDate.of(2025, 12, 24), LocalDate.of(2025, 12, 24)));
        assertThat(plan.forecastRange()).isEmpty();
    }

    @Test
    void aShootAlmostAYearOutGoesBackTwoYearsToStayClearOfTheArchiveDelay() {
        LocalDate shoot = LocalDate.of(2027, 9, 20); // a year earlier is 2026-09-20: only two days ago

        assertThat(plan(shoot).entries().getFirst().referenceDate()).isEqualTo(LocalDate.of(2025, 9, 20));
    }

    @Test
    void theTwentyNinthOfFebruaryFallsBackToTheTwentyEighth() {
        assertThat(plan(LocalDate.of(2028, 2, 29)).entries().getFirst().referenceDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void recentPastDaysAreRecordedByTheForecastModelAndOlderOnesByTheArchive() {
        LocalDate yesterday = TODAY.minusDays(1);
        LocalDate lastMonth = TODAY.minusDays(30);
        LocalDate longAgo = TODAY.minusDays(31);

        WeatherPlan plan = plan(longAgo, lastMonth, yesterday);

        assertThat(plan.entries()).containsExactly(
                new Entry(longAgo, Basis.RECORDED, longAgo, false),
                new Entry(lastMonth, Basis.RECORDED, lastMonth, true),
                new Entry(yesterday, Basis.RECORDED, yesterday, true));
    }

    @Test
    void aWindowAcrossTheHorizonIsSplitBetweenBothSources() {
        WeatherPlan plan = plan(TODAY.plusDays(14), TODAY.plusDays(15), TODAY.plusDays(16), TODAY.plusDays(17));

        assertThat(plan.forecastRange()).contains(new Range(TODAY.plusDays(14), TODAY.plusDays(15)));
        assertThat(plan.historyRange()).contains(new Range(TODAY.plusDays(16).minusYears(1), TODAY.plusDays(17).minusYears(1)));
    }
}
