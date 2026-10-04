package com.cinescout.service;

import com.cinescout.domain.Project;
import com.cinescout.domain.Scene;
import com.cinescout.domain.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleConflictsTest {

    private static Scene scene(String start, String end, String call, String wrap) {
        Scene scene = new Scene(new Project(new User("ada@example.com", "hash", "Ada"), "Neon Nights", null), "Diner", "INT. DINER");
        scene.setShootDateStart(start == null ? null : LocalDate.parse(start));
        scene.setShootDateEnd(end == null ? null : LocalDate.parse(end));
        scene.setCallTime(call == null ? null : LocalTime.parse(call));
        scene.setWrapTime(wrap == null ? null : LocalTime.parse(wrap));
        return scene;
    }

    @Test
    void timesOverlapOnlyWhenBothWindowsAreKnownAndMeet() {
        assertThat(ScheduleConflicts.timesOverlap(scene(null, null, "07:00", "12:00"), scene(null, null, "11:00", "15:00"))).isTrue();
        assertThat(ScheduleConflicts.timesOverlap(scene(null, null, "07:00", "12:00"), scene(null, null, "12:00", "15:00"))).isFalse();
        assertThat(ScheduleConflicts.timesOverlap(scene(null, null, "07:00", "12:00"), scene(null, null, null, "15:00"))).isNull();
    }

    @Test
    void aWrapAtOrBeforeTheCallIsTheNextMorning() {
        assertThat(ScheduleConflicts.timesOverlap(scene(null, null, "22:00", "04:00"), scene(null, null, "23:30", "23:45"))).isTrue();
        assertThat(ScheduleConflicts.timesOverlap(scene(null, null, "22:00", "04:00"), scene(null, null, "08:00", "12:00"))).isFalse();
    }

    @Test
    void aScenesDaysRunFromItsFirstToItsLastAndAreCapped() {
        assertThat(ScheduleConflicts.days(scene("2026-11-02", "2026-11-04", null, null)))
                .containsExactly(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-03"), LocalDate.parse("2026-11-04"));
        assertThat(ScheduleConflicts.days(scene(null, "2026-11-04", null, null))).containsExactly(LocalDate.parse("2026-11-04"));
        assertThat(ScheduleConflicts.days(scene(null, null, null, null))).isEmpty();
        assertThat(ScheduleConflicts.days(scene("2026-01-01", "2026-12-31", null, null))).hasSize(31);
    }

    @Test
    void workingDaysBeforeSkipTheWeekend() {
        // Monday 2 November 2026: five working days before is the Monday before.
        assertThat(ScheduleConflicts.workingDaysBefore(LocalDate.parse("2026-11-02"), 5)).isEqualTo(LocalDate.parse("2026-10-26"));
        assertThat(ScheduleConflicts.workingDaysBefore(LocalDate.parse("2026-11-02"), 1)).isEqualTo(LocalDate.parse("2026-10-30"));
        assertThat(ScheduleConflicts.workingDaysBefore(LocalDate.parse("2026-11-02"), 0)).isEqualTo(LocalDate.parse("2026-11-02"));
    }
}
