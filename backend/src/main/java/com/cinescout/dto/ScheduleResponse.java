package com.cinescout.dto;

import com.cinescout.domain.Location;
import com.cinescout.domain.Scene;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A project's shoot as a calendar: which scenes start on which day, and where each will be shot.
 *
 * @param days        the days on which scenes start shooting, earliest first
 * @param unscheduled scenes without shoot dates, in script order
 */
public record ScheduleResponse(List<ShootDay> days, List<ScheduledScene> unscheduled) {

    /** @param scenes the scenes whose shoot starts that day, in script order */
    public record ShootDay(LocalDate date, List<ScheduledScene> scenes) {
    }

    /**
     * @param shootDateStart null when the scene has only a last day
     * @param shootDateEnd   null when the scene has only a first day
     * @param settingType    the kind of place the scene needs, once it has been analysed
     * @param venues         the scene's confirmed locations; empty while none is confirmed
     * @param candidates     how many candidate locations the scene has in all
     */
    public record ScheduledScene(
            UUID id,
            Integer sceneNumber,
            String title,
            LocalDate shootDateStart,
            LocalDate shootDateEnd,
            String settingType,
            String timeOfDay,
            List<Venue> venues,
            long candidates
    ) {

        /** The day the scene is listed under: its first shoot day, or its last if that is all it has. */
        private static LocalDate dayOf(Scene scene) {
            return scene.getShootDateStart() != null ? scene.getShootDateStart() : scene.getShootDateEnd();
        }

        public static ScheduledScene from(Scene scene, List<Location> confirmed, long candidates) {
            var requirements = scene.requirements();
            return new ScheduledScene(scene.getId(), scene.getSceneNumber(), scene.getTitle(),
                    scene.getShootDateStart(), scene.getShootDateEnd(),
                    requirements == null ? null : requirements.settingType(),
                    requirements == null ? null : requirements.timeOfDay(),
                    confirmed.stream().map(location -> Venue.from(location, dayOf(scene))).toList(), candidates);
        }
    }

    /**
     * A confirmed location, with who to call there on the day when the user has recorded it.
     *
     * @param day the light and weather at the venue on the scene's day, from its logistics report; null when the
     *            report has not been worked out, or does not cover that day
     */
    public record Venue(UUID id, String name, String address, BigDecimal latitude, BigDecimal longitude,
                        String contactName, String contactPhone, DayConditions day) {

        static Venue from(Location location, LocalDate date) {
            return new Venue(location.getId(), location.getName(), location.getAddress(), location.getLatitude(), location.getLongitude(),
                    location.getContactName(), location.getContactPhone(), DayConditions.of(location.getLogisticsJson(), date));
        }
    }

    /**
     * One day at a venue, as a call sheet gives it. Times are the venue's local clock ("07:04"), taken as they
     * stand in the report, which already speaks the venue's time zone.
     */
    public record DayConditions(String sunrise, String sunset, String weather, Double temperatureMinC, Double temperatureMaxC) {

        /** The conditions on {@code date} in a logistics report; null when there is no report, or it does not cover the date. */
        public static DayConditions of(JsonNode report, LocalDate date) {
            if (report == null || date == null) {
                return null;
            }
            String day = date.toString();
            JsonNode solar = find(report.path("solar").path("days"), day);
            JsonNode weather = find(report.path("weather").path("days"), day);
            if (solar == null && weather == null) {
                return null;
            }
            return new DayConditions(clock(solar, "sunrise"), clock(solar, "sunset"), text(weather, "summary"),
                    number(weather, "temperatureMinC"), number(weather, "temperatureMaxC"));
        }

        private static JsonNode find(JsonNode days, String date) {
            for (JsonNode day : days) {
                if (date.equals(day.path("date").asText())) {
                    return day;
                }
            }
            return null;
        }

        /** "2026-10-12T07:04:00-04:00" as "07:04". */
        private static String clock(JsonNode day, String field) {
            String time = text(day, field);
            return time == null || time.length() < 16 ? null : time.substring(11, 16);
        }

        private static String text(JsonNode day, String field) {
            JsonNode value = day == null ? null : day.get(field);
            return value == null || value.isNull() ? null : value.asText();
        }

        private static Double number(JsonNode day, String field) {
            JsonNode value = day == null ? null : day.get(field);
            return value == null || !value.isNumber() ? null : value.asDouble();
        }
    }
}
