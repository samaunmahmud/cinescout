package com.cinescout.calendar;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.domain.Location;
import com.cinescout.domain.Scene;
import com.cinescout.domain.User;
import com.cinescout.dto.CalendarLinkResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.SceneRepository;
import com.cinescout.repository.UserRepository;
import com.cinescout.service.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A person's calendar feed: the shoot days of every active project they are on, as an iCalendar document behind a
 * secret link that a calendar app subscribes to. The link is a random 256-bit token; a new one replaces the old, and
 * turning the feed off clears it. Each event carries the confirmed venue and address, who to call there, the call and
 * wrap times when set, and a link to the call sheet; never notes, scores or script text.
 */
@Service
public class CalendarFeedService {

    /** The most scenes a feed lists, the earliest first. */
    static final int MAX_SCENES = 500;
    /** A scene with a call time is one event a shoot day, for at most this many days. */
    static final int MAX_DAYS_A_SCENE = 31;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter UID_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final UserRepository users;
    private final SceneRepository scenes;
    private final LocationRepository locations;
    private final BlockingTransactions db;

    public CalendarFeedService(UserRepository users, SceneRepository scenes, LocationRepository locations, BlockingTransactions db) {
        this.users = users;
        this.scenes = scenes;
        this.locations = locations;
        this.db = db;
    }

    /** @throws NotFoundException (as an error signal) while the feed is off */
    public Mono<CalendarLinkResponse> link(UUID userId) {
        return db.call(() -> {
            String token = users.findById(userId).map(User::getCalendarToken).orElse(null);
            if (token == null) {
                throw new NotFoundException("Your calendar feed is off");
            }
            return new CalendarLinkResponse(token);
        });
    }

    /** A new link, which replaces the old one if there was one. */
    public Mono<CalendarLinkResponse> create(UUID userId) {
        return db.call(() -> {
            User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
            byte[] secret = new byte[32];
            RANDOM.nextBytes(secret);
            user.setCalendarToken(Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
            return new CalendarLinkResponse(users.saveAndFlush(user).getCalendarToken());
        });
    }

    /** Turns the feed off: the link stops working at once. Doing it twice is harmless. */
    public Mono<Void> turnOff(UUID userId) {
        return db.run(() -> users.findById(userId).ifPresent(user -> {
            user.setCalendarToken(null);
            users.saveAndFlush(user);
        }));
    }

    /**
     * The feed as an iCalendar document.
     *
     * @param appUrl where the web app is, e.g. {@code https://cinescout.example.com}, for the links to call sheets
     * @throws NotFoundException (as an error signal) for a token that is not, or no longer, a feed
     */
    public Mono<String> feed(String token, String appUrl) {
        return db.call(() -> {
            User user = TOKEN.matcher(token).matches() ? users.findByCalendarToken(token).orElse(null) : null;
            if (user == null) {
                throw new NotFoundException("This calendar feed does not exist, or was turned off");
            }
            List<Scene> dated = scenes.findDatedForMember(user.getId(), PageRequest.of(0, MAX_SCENES));
            Map<UUID, List<Location>> venues = dated.isEmpty() ? Map.of()
                    : locations.findConfirmedByScenes(dated.stream().map(Scene::getId).toList()).stream()
                    .collect(Collectors.groupingBy(location -> location.getScene().getId()));
            return write(dated, venues, appUrl, DatabaseTime.now());
        });
    }

    static String write(List<Scene> dated, Map<UUID, List<Location>> venues, String appUrl, Instant now) {
        IcsWriter ics = new IcsWriter()
                .raw("BEGIN", "VCALENDAR")
                .raw("VERSION", "2.0")
                .text("PRODID", "-//CineScout//Shoot days//EN")
                .raw("CALSCALE", "GREGORIAN")
                .text("X-WR-CALNAME", "CineScout shoot days");
        for (Scene scene : dated) {
            event(ics, scene, venues.getOrDefault(scene.getId(), List.of()), appUrl, now);
        }
        return ics.raw("END", "VCALENDAR").toString();
    }

    private static void event(IcsWriter ics, Scene scene, List<Location> confirmed, String appUrl, Instant now) {
        LocalDate first = scene.getShootDateStart() != null ? scene.getShootDateStart() : scene.getShootDateEnd();
        LocalDate last = scene.getShootDateEnd() != null && !scene.getShootDateEnd().isBefore(first) ? scene.getShootDateEnd() : first;
        String callSheet = appUrl + "/projects/" + scene.getProject().getId() + "/call-sheet";
        String summary = scene.getProject().getTitle() + ": " + label(scene);
        String where = confirmed.isEmpty() ? "Location TBC"
                : confirmed.stream().map(venue -> join(", ", venue.getName(), venue.getAddress())).collect(Collectors.joining("; "));
        String description = description(scene, confirmed, callSheet);
        String status = confirmed.isEmpty() ? "TENTATIVE" : "CONFIRMED";

        LocalTime call = scene.getCallTime();
        if (call == null) {
            ics.raw("BEGIN", "VEVENT").text("UID", "scene-" + scene.getId() + "@cinescout").utcTime("DTSTAMP", now)
                    .date("DTSTART", first).date("DTEND", last.plusDays(1));
            details(ics, summary, where, description, callSheet, status);
            return;
        }
        LocalTime wrap = scene.getWrapTime();
        for (LocalDate day = first; !day.isAfter(last) && !day.isAfter(first.plusDays(MAX_DAYS_A_SCENE - 1)); day = day.plusDays(1)) {
            ics.raw("BEGIN", "VEVENT").text("UID", "scene-" + scene.getId() + "-" + UID_DAY.format(day) + "@cinescout")
                    .utcTime("DTSTAMP", now).localTime("DTSTART", day.atTime(call));
            if (wrap != null) {
                // A wrap at or before the call is the next morning.
                ics.localTime("DTEND", (wrap.isAfter(call) ? day : day.plusDays(1)).atTime(wrap));
            }
            details(ics, summary, where, description, callSheet, status);
        }
    }

    private static void details(IcsWriter ics, String summary, String where, String description, String callSheet, String status) {
        ics.text("SUMMARY", summary).text("LOCATION", where).text("DESCRIPTION", description).raw("URL", callSheet).raw("STATUS", status)
                .raw("TRANSP", "OPAQUE").raw("END", "VEVENT");
    }

    private static String description(Scene scene, List<Location> confirmed, String callSheet) {
        List<String> lines = new ArrayList<>();
        lines.add("Project: " + scene.getProject().getTitle());
        if (confirmed.isEmpty()) {
            lines.add("No location confirmed yet");
        }
        for (Location venue : confirmed) {
            lines.add("Location: " + join(", ", venue.getName(), venue.getAddress()));
            String contact = join(", ", venue.getContactName(), venue.getContactPhone());
            if (!contact.isEmpty()) {
                lines.add("Contact: " + contact);
            }
        }
        if (scene.getCallTime() != null) {
            lines.add("Call " + CLOCK.format(scene.getCallTime()) + (scene.getWrapTime() == null ? "" : ", wrap " + CLOCK.format(scene.getWrapTime())));
        }
        lines.add("Call sheet: " + callSheet);
        return String.join("\n", lines);
    }

    /** "Sc. 12 INT. DINER - NIGHT", or the title alone when the scene has no number. */
    private static String label(Scene scene) {
        return scene.getSceneNumber() == null ? scene.getTitle() : "Sc. " + scene.getSceneNumber() + " " + scene.getTitle();
    }

    private static String join(String separator, String... parts) {
        return Stream.of(parts).filter(Objects::nonNull).map(String::strip).filter(part -> !part.isEmpty()).collect(Collectors.joining(separator));
    }
}
