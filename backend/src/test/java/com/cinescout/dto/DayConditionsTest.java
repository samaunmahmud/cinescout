package com.cinescout.dto;

import com.cinescout.dto.ScheduleResponse.DayConditions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class DayConditionsTest {

    private static JsonNode report(String sunPath) throws Exception {
        return new ObjectMapper().readTree("""
                {"solar":{"days":[{"date":"2026-10-12","sunrise":"2026-10-12T07:14:00+01:00","sunset":"2026-10-12T18:13:00+01:00"%s}]},
                 "weather":{"days":[]}}""".formatted(sunPath == null ? "" : ",\"sunPath\":" + sunPath));
    }

    @Test
    void theSunsPathIsReadFromTheReportForTheCallSheet() throws Exception {
        DayConditions day = DayConditions.of(report("""
                [{"at":"2026-10-12T16:00:00+01:00","azimuth":225.2,"elevation":18.4,"compass":"SW","text":"Sun from SW (225°), 18° high at 16:00"}]"""),
                LocalDate.of(2026, 10, 12));

        assertThat(day.sun()).hasSize(1);
        assertThat(day.sun().getFirst().time()).isEqualTo("16:00");
        assertThat(day.sun().getFirst().compass()).isEqualTo("SW");
        assertThat(day.sun().getFirst().text()).isEqualTo("Sun from SW (225°), 18° high at 16:00");
        assertThat(day.sunFrom(LocalTime.of(16, 0))).isTrue();
        assertThat(day.sunFrom(LocalTime.of(7, 30))).isFalse();
        assertThat(day.sunFrom(null)).isTrue();
    }

    @Test
    void aReportFromBeforeTheSunPathHasNoneAndIsStaleOnceACallTimeIsSet() throws Exception {
        DayConditions day = DayConditions.of(report(null), LocalDate.of(2026, 10, 12));

        assertThat(day.sun()).isEmpty();
        assertThat(day.sunFrom(LocalTime.of(7, 30))).isFalse();
        assertThat(day.sunFrom(null)).isTrue();
    }
}
