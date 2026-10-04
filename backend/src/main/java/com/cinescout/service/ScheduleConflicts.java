package com.cinescout.service;

import com.cinescout.domain.AvailabilityState;
import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.domain.Location;
import com.cinescout.domain.Scene;
import com.cinescout.domain.VenueAvailability;
import com.cinescout.domain.VenueNames;
import com.cinescout.dto.ScheduleResponse.Conflict;
import com.cinescout.dto.ScheduleResponse.ConflictKind;
import com.cinescout.permits.FilmingOffices;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What stands in the way of a project's schedule, from its scenes, their confirmed venues and what is known of those
 * venues' days. A venue is one place however many scenes have it as a candidate, so holds and clashes are matched
 * across scenes by name or street address, as scouting matches duplicates ({@link VenueNames}).
 * <ul>
 *   <li>a confirmed venue marked unavailable on one of its scene's shoot days (a problem);</li>
 *   <li>a pencil or hold on a shoot day that lapses before that day (a problem);</li>
 *   <li>two scenes at one venue on one day at times that overlap, or are not set (a warning).</li>
 * </ul>
 */
final class ScheduleConflicts {

    /** A scene's shoot window may be long; nobody books a venue for more than this many days of it. */
    private static final int MAX_DAYS = 31;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final int MINUTES_A_DAY = 24 * 60;

    private final List<VenueAvailability> holds;
    private final FilmingOffices offices;
    private final LocalDate today;

    ScheduleConflicts(List<VenueAvailability> holds, FilmingOffices offices, LocalDate today) {
        this.holds = holds;
        this.offices = offices;
        this.today = today;
    }

    ScheduleConflicts(List<VenueAvailability> holds) {
        this(holds, null, null);
    }

    /** What is known of the venue (or the same venue on another scene, if not of this one) on {@code day}. */
    Optional<VenueAvailability> on(Location venue, LocalDate day) {
        VenueAvailability other = null;
        for (VenueAvailability hold : holds) {
            if (!hold.getDay().equals(day)) {
                continue;
            }
            if (hold.getLocation().getId().equals(venue.getId())) {
                return Optional.of(hold);
            }
            if (other == null && sameVenue(hold.getLocation(), venue)) {
                other = hold;
            }
        }
        return Optional.ofNullable(other);
    }

    /** The holds on each confirmed venue's first shoot day, by venue id, for the schedule to show. */
    Map<UUID, VenueAvailability> firstDays(List<Scene> scenes, Map<UUID, List<Location>> confirmed) {
        Map<UUID, VenueAvailability> first = new HashMap<>();
        for (Scene scene : scenes) {
            List<LocalDate> days = days(scene);
            if (days.isEmpty()) {
                continue;
            }
            for (Location venue : confirmed.getOrDefault(scene.getId(), List.of())) {
                on(venue, days.getFirst()).ifPresent(hold -> first.put(venue.getId(), hold));
            }
        }
        return first;
    }

    /** Every conflict, by date, the problems on a day before its warnings. */
    List<Conflict> find(List<Scene> scenes, Map<UUID, List<Location>> confirmed) {
        // One hold seen from two scenes at the same venue is one conflict, naming both scenes.
        Map<String, Conflict> problems = new LinkedHashMap<>();
        for (Scene scene : scenes) {
            for (Location venue : confirmed.getOrDefault(scene.getId(), List.of())) {
                for (LocalDate day : days(scene)) {
                    on(venue, day).flatMap(hold -> problem(scene, venue, day, hold)).ifPresent(conflict ->
                            problems.merge(conflict.kind() + "|" + conflict.date() + "|" + conflict.message(), conflict, ScheduleConflicts::bothScenes));
                }
            }
        }
        List<Conflict> found = new ArrayList<>(problems.values());
        for (Scene scene : scenes) {
            List<LocalDate> days = days(scene);
            if (days.isEmpty()) {
                continue;
            }
            for (Location venue : confirmed.getOrDefault(scene.getId(), List.of())) {
                permit(scene, venue, days.getFirst()).ifPresent(found::add);
            }
        }
        for (int i = 0; i < scenes.size(); i++) {
            for (int j = i + 1; j < scenes.size(); j++) {
                clash(scenes.get(i), scenes.get(j), confirmed).ifPresent(found::add);
            }
        }
        found.sort(Comparator.comparing(Conflict::date).thenComparing(conflict -> !conflict.problem()));
        return found;
    }

    /**
     * For a confirmed venue marked as a public space whose area is known: when to apply to its filming office by, or
     * that the notice period has run out. A venue whose area has not been looked up yet says nothing.
     */
    private Optional<Conflict> permit(Scene scene, Location venue, LocalDate firstDay) {
        if (offices == null || venue.getBookingFriction() != BookingFriction.PUBLIC || venue.getAdminArea() == null) {
            return Optional.empty();
        }
        Optional<FilmingOffices.Office> office = offices.officeFor(venue.getAdminArea());
        if (office.isEmpty()) {
            if (!"gb".equals(venue.getAdminArea().countryCode())) {
                return Optional.empty();
            }
            return Optional.of(new Conflict(ConflictKind.PERMIT_LEAD_TIME, false, firstDay, List.of(scene.getId()), venue.getId(), venue.getName(),
                    "%s is a public space: ask the local council about filming permission well before %s."
                            .formatted(venue.getName(), DAY.format(firstDay))));
        }
        SceneRequirements needs = scene.requirements();
        Integer lead = office.get().leadTimeFor(needs == null ? null : needs.estimatedCastAndCrewSize());
        if (lead == null) {
            return Optional.of(new Conflict(ConflictKind.PERMIT_LEAD_TIME, false, firstDay, List.of(scene.getId()), venue.getId(), venue.getName(),
                    "%s is a public space in %s: ask %s about filming permission well before %s."
                            .formatted(venue.getName(), office.get().area(), office.get().office(), DAY.format(firstDay))));
        }
        LocalDate applyBy = workingDaysBefore(firstDay, lead);
        boolean late = today != null && applyBy.isBefore(today);
        String message = late
                ? "%s is a public space in %s, which asks for %d working days' notice: that ran out on %s. Call %s now."
                        .formatted(venue.getName(), office.get().area(), lead, DAY.format(applyBy), office.get().office())
                : "%s is a public space in %s: apply to %s by %s (%d working days ahead)."
                        .formatted(venue.getName(), office.get().area(), office.get().office(), DAY.format(applyBy), lead);
        return Optional.of(new Conflict(ConflictKind.PERMIT_LEAD_TIME, late, firstDay, List.of(scene.getId()), venue.getId(), venue.getName(),
                message));
    }

    /** {@code days} working days (Monday to Friday; bank holidays not counted out) before {@code day}. */
    static LocalDate workingDaysBefore(LocalDate day, int days) {
        LocalDate date = day;
        int left = days;
        while (left > 0) {
            date = date.minusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                left--;
            }
        }
        return date;
    }

    private static Conflict bothScenes(Conflict first, Conflict again) {
        List<UUID> scenes = new ArrayList<>(first.sceneIds());
        again.sceneIds().stream().filter(id -> !scenes.contains(id)).forEach(scenes::add);
        return new Conflict(first.kind(), first.problem(), first.date(), List.copyOf(scenes), first.locationId(), first.venueName(),
                first.message());
    }

    private static Optional<Conflict> problem(Scene scene, Location venue, LocalDate day, VenueAvailability hold) {
        if (hold.getState() == AvailabilityState.UNAVAILABLE) {
            return Optional.of(new Conflict(ConflictKind.UNAVAILABLE, true, day, List.of(scene.getId()), venue.getId(), venue.getName(),
                    "%s is marked unavailable on %s, a shoot day of %s.".formatted(venue.getName(), DAY.format(day), label(scene))));
        }
        LocalDate lapses = hold.getHoldExpiresOn();
        if (hold.getState().canExpire() && lapses != null && lapses.isBefore(day)) {
            String what = hold.getState() == AvailabilityState.HELD ? "hold" : "pencil";
            return Optional.of(new Conflict(ConflictKind.HOLD_EXPIRES, true, day, List.of(scene.getId()), venue.getId(), venue.getName(),
                    "The %s on %s for %s lapses on %s, before the shoot. Renew it or confirm the booking."
                            .formatted(what, venue.getName(), DAY.format(day), DAY.format(lapses))));
        }
        return Optional.empty();
    }

    private static Optional<Conflict> clash(Scene a, Scene b, Map<UUID, List<Location>> confirmed) {
        List<LocalDate> shared = new ArrayList<>(days(a));
        shared.retainAll(days(b));
        if (shared.isEmpty()) {
            return Optional.empty();
        }
        Boolean overlap = timesOverlap(a, b);
        if (Boolean.FALSE.equals(overlap)) {
            return Optional.empty();
        }
        for (Location venue : confirmed.getOrDefault(a.getId(), List.of())) {
            for (Location other : confirmed.getOrDefault(b.getId(), List.of())) {
                if (venue.getId().equals(other.getId()) || sameVenue(venue, other)) {
                    LocalDate day = shared.getFirst();
                    String both = "%s and %s are both at %s on %s".formatted(capitalised(label(a)), label(b), venue.getName(), DAY.format(day));
                    String message = overlap == null
                            ? both + "; set their call and wrap times to check they do not overlap."
                            : both + ", at overlapping times (%s and %s).".formatted(window(a), window(b));
                    return Optional.of(new Conflict(ConflictKind.DOUBLE_BOOKED, false, day, List.of(a.getId(), b.getId()), venue.getId(),
                            venue.getName(), message));
                }
            }
        }
        return Optional.empty();
    }

    /** True or false when both scenes have a call and a wrap time; null when that cannot be told. */
    static Boolean timesOverlap(Scene a, Scene b) {
        int[] first = minutes(a);
        int[] second = minutes(b);
        if (first == null || second == null) {
            return null;
        }
        return first[0] < second[1] && second[0] < first[1];
    }

    /** Call and wrap as minutes from midnight; a wrap at or before the call is the next morning. */
    private static int[] minutes(Scene scene) {
        LocalTime call = scene.getCallTime();
        LocalTime wrap = scene.getWrapTime();
        if (call == null || wrap == null) {
            return null;
        }
        int start = call.getHour() * 60 + call.getMinute();
        int end = wrap.getHour() * 60 + wrap.getMinute();
        return new int[] {start, end <= start ? end + MINUTES_A_DAY : end};
    }

    /** The scene's shoot days, at most {@value #MAX_DAYS} of them; empty when it has no dates. */
    static List<LocalDate> days(Scene scene) {
        LocalDate start = scene.getShootDateStart() != null ? scene.getShootDateStart() : scene.getShootDateEnd();
        LocalDate end = scene.getShootDateEnd() != null ? scene.getShootDateEnd() : start;
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = start; day != null && !day.isAfter(end) && days.size() < MAX_DAYS; day = day.plusDays(1)) {
            days.add(day);
        }
        return days;
    }

    static boolean sameVenue(Location a, Location b) {
        return VenueNames.sameVenue(a.getName(), b.getName()) || VenueNames.sameStreetAddress(a.getAddress(), b.getAddress());
    }

    private static String label(Scene scene) {
        return scene.getSceneNumber() != null ? "scene " + scene.getSceneNumber() : "“" + scene.getTitle() + "”";
    }

    private static String capitalised(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String window(Scene scene) {
        return scene.getCallTime() + "–" + scene.getWrapTime();
    }
}
