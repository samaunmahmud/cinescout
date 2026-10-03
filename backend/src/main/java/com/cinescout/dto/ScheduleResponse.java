package com.cinescout.dto;

import com.cinescout.domain.AvailabilityState;
import com.cinescout.domain.Location;
import com.cinescout.domain.VenueAvailability;
import com.cinescout.domain.Scene;
import com.cinescout.script.ScriptCharacters;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A project's shoot as a calendar: which scenes start on which day, and where each will be shot.
 *
 * @param days        the days on which scenes start shooting, earliest first
 * @param unscheduled scenes without shoot dates, in script order
 * @param conflicts   what stands in the way of the schedule as it is (a venue unavailable on a shoot day, a hold
 *                    lapsing before it) and what may (two scenes at one venue at once), by date
 */
public record ScheduleResponse(List<ShootDay> days, List<ScheduledScene> unscheduled, List<Conflict> conflicts) {

    /** The schedule for people outside the production office: no holds, no conflicts. */
    public ScheduleResponse withoutBookings() {
        return new ScheduleResponse(days.stream().map(day -> new ShootDay(day.date(), day.scenes().stream().map(ScheduledScene::withoutBookings).toList())).toList(),
                unscheduled.stream().map(ScheduledScene::withoutBookings).toList(), List.of());
    }

    public enum ConflictKind {
        /** A confirmed venue is marked unavailable on one of the scene's shoot days. */
        UNAVAILABLE,
        /** A pencil or hold on a shoot day lapses before that day. */
        HOLD_EXPIRES,
        /** Two scenes are at the same venue on the same day, at times that overlap or are not set. A warning only. */
        DOUBLE_BOOKED
    }

    /**
     * @param problem    true when it must be sorted out (unavailable, a hold lapsing); false for a warning
     * @param sceneIds   the scene, or both scenes of a double booking
     * @param locationId the venue as confirmed for the (first) scene
     * @param message    the conflict in plain English
     */
    public record Conflict(ConflictKind kind, boolean problem, LocalDate date, List<UUID> sceneIds, UUID locationId, String venueName,
                           String message) {
    }

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
            LocalTime callTime,
            LocalTime wrapTime,
            String settingType,
            String timeOfDay,
            List<String> characters,
            List<Venue> venues,
            long candidates
    ) {

        /** The day the scene is listed under: its first shoot day, or its last if that is all it has. */
        private static LocalDate dayOf(Scene scene) {
            return scene.getShootDateStart() != null ? scene.getShootDateStart() : scene.getShootDateEnd();
        }

        /**
         * @param holds what is known of a confirmed venue's state on the scene's (first) day, by venue id; may be
         *              missing a venue
         */
        public static ScheduledScene from(Scene scene, List<Location> confirmed, long candidates, Map<UUID, VenueAvailability> holds) {
            var requirements = scene.requirements();
            return new ScheduledScene(scene.getId(), scene.getSceneNumber(), scene.getTitle(),
                    scene.getShootDateStart(), scene.getShootDateEnd(), scene.getCallTime(), scene.getWrapTime(),
                    requirements == null ? null : requirements.settingType(),
                    requirements == null ? null : requirements.timeOfDay(),
                    ScriptCharacters.in(scene.getSourceText()),
                    confirmed.stream().map(location -> Venue.from(location, dayOf(scene), holds.get(location.getId()))).toList(), candidates);
        }

        public static ScheduledScene from(Scene scene, List<Location> confirmed, long candidates) {
            return from(scene, confirmed, candidates, Map.of());
        }

        ScheduledScene withoutBookings() {
            return new ScheduledScene(id, sceneNumber, title, shootDateStart, shootDateEnd, callTime, wrapTime, settingType, timeOfDay,
                    characters, venues.stream().map(Venue::withoutBookings).toList(), candidates);
        }
    }

    /**
     * A confirmed location, with who to call there on the day when the user has recorded it.
     *
     * @param day     the light and weather at the venue on the scene's day, from its logistics report; null when the
     *                report has not been worked out, or does not cover that day
     * @param booking the venue's state on the scene's day (pencilled, held, confirmed, unavailable); null when not recorded
     */
    public record Venue(UUID id, String name, String address, BigDecimal latitude, BigDecimal longitude,
                        String contactName, String contactPhone, DayConditions day, Booking booking) {

        static Venue from(Location location, LocalDate date, VenueAvailability hold) {
            return new Venue(location.getId(), location.getName(), location.getAddress(), location.getLatitude(), location.getLongitude(),
                    location.getContactName(), location.getContactPhone(), DayConditions.of(location.getLogisticsJson(), date),
                    hold == null ? null : new Booking(hold.getState(), hold.getHoldExpiresOn()));
        }

        Venue withoutBookings() {
            return new Venue(id, name, address, latitude, longitude, contactName, contactPhone, day, null);
        }
    }

    /** @param holdExpiresOn when a pencil or hold lapses; null for none */
    public record Booking(AvailabilityState state, LocalDate holdExpiresOn) {
    }

    /**
     * One day at a venue, as a call sheet gives it, with the weather's warnings for the shoot ("Rain likely: plan cover"). Times are the venue's local clock ("07:04"), taken as they
     * stand in the report, which already speaks the venue's time zone.
     */
    public record DayConditions(String sunrise, String sunset, String weather, Double temperatureMinC, Double temperatureMaxC,
                                List<String> warnings) {

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
            List<String> warnings = new ArrayList<>();
            if (weather != null) {
                weather.path("warnings").forEach(warning -> warnings.add(warning.asText()));
            }
            return new DayConditions(clock(solar, "sunrise"), clock(solar, "sunset"), text(weather, "summary"),
                    number(weather, "temperatureMinC"), number(weather, "temperatureMaxC"), List.copyOf(warnings));
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
