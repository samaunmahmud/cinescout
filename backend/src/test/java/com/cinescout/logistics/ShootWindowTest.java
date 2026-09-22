package com.cinescout.logistics;

import com.cinescout.logistics.LogisticsReport.ShootWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ShootWindowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);

    @Test
    void theScenesDatesAreUsedAsTheyAre() {
        assertThat(LogisticsService.window(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), TODAY, 14))
                .isEqualTo(new ShootWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), false, false));
    }

    @Test
    void withoutDatesTheComingWeekIsAssumed() {
        assertThat(LogisticsService.window(null, null, TODAY, 14))
                .isEqualTo(new ShootWindow(TODAY, TODAY.plusDays(6), true, false));
    }

    @Test
    void oneDateMakesAOneDayWindow() {
        LocalDate day = LocalDate.of(2026, 10, 1);
        assertThat(LogisticsService.window(day, null, TODAY, 14)).isEqualTo(new ShootWindow(day, day, false, false));
        assertThat(LogisticsService.window(null, day, TODAY, 14)).isEqualTo(new ShootWindow(day, day, false, false));
    }

    @Test
    void aLongWindowIsCutToItsFirstDays() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        assertThat(LogisticsService.window(start, LocalDate.of(2026, 12, 31), TODAY, 14))
                .isEqualTo(new ShootWindow(start, start.plusDays(13), false, true));
        assertThat(LogisticsService.window(start, start.plusDays(13), TODAY, 14).truncated()).isFalse();
    }
}
