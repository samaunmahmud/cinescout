package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.validate.ValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The calendar feed: its secret link, and an RFC 5545 document of the person's shoot days that ical4j parses and validates. */
class CalendarApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner, String title) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", title))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String scene(Account owner, String projectId, int number, String title, String start, String end, String call, String wrap) {
        String id = json(owner.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("sceneNumber", number, "title", title, "sourceText", "SECRET-SCRIPT the twist is the butler"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        Map<String, Object> dates = new HashMap<>();
        dates.put("shootDateStart", start);
        dates.put("shootDateEnd", end);
        dates.put("callTime", call);
        dates.put("wrapTime", wrap);
        owner.client().put().uri("/api/scenes/" + id + "/shoot-dates").bodyValue(dates).exchange().expectStatus().isOk();
        return id;
    }

    private void confirmedVenue(Account owner, String sceneId, String name, String address) {
        String id = json(owner.client().post().uri("/api/scenes/" + sceneId + "/locations")
                .bodyValue(Map.of("name", name, "address", address, "notes", "PRIVATE-NOTE cash only"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        owner.client().put().uri("/api/locations/" + id + "/contact")
                .bodyValue(Map.of("name", "Tom Miller", "phone", "+1 718 555 0100")).exchange().expectStatus().isOk();
        owner.client().put().uri("/api/locations/" + id).bodyValue(Map.of("status", "CONFIRMED")).exchange().expectStatus().isOk();
    }

    private String feedLink(Account who) {
        return json(who.client().post().uri("/api/account/calendar-link").exchange().expectStatus().isOk()).path("token").asText();
    }

    private ResponseSpec feed(String token) {
        return web.get().uri("/api/public/calendars/" + token + ".ics").exchange();
    }

    private static Calendar parse(String ics) throws Exception {
        return new CalendarBuilder().build(new StringReader(ics));
    }

    private static String value(VEvent event, String property) {
        return event.getProperty(property).map(Property::getValue).orElse(null);
    }

    @Test
    void theFeedListsEachShootDayWithItsVenueContactAndCallSheetAndValidates() throws Exception {
        Account ada = register("Ada");
        String project = project(ada, "Night Shift");
        String diner = scene(ada, project, 12, "INT. DINER - NIGHT", "2026-10-12", "2026-10-13", "19:30", "05:00");
        confirmedVenue(ada, diner, "Tom's Diner", "782 Washington Ave, Brooklyn, NY");
        scene(ada, project, 13, "EXT. ROOFTOP - DAWN", "2026-10-14", "2026-10-15", null, null);
        scene(ada, project, 14, "Undated", null, null, null, null);
        String token = feedLink(ada);

        String ics = feed(token).expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.parseMediaType("text/calendar"))
                .expectHeader().valueEquals("X-Robots-Tag", "noindex")
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).doesNotContain("SECRET-SCRIPT").doesNotContain("PRIVATE-NOTE");
        Calendar calendar = parse(ics);
        ValidationResult validation = calendar.validate();
        assertThat(validation.hasErrors()).as(validation.toString()).isFalse();

        List<VEvent> events = calendar.getComponents("VEVENT");
        assertThat(events).hasSize(3); // two diner nights, one rooftop run
        VEvent first = events.get(0);
        assertThat(value(first, "SUMMARY")).isEqualTo("Night Shift: Sc. 12 INT. DINER - NIGHT");
        assertThat(value(first, "DTSTART")).isEqualTo("20261012T193000");
        assertThat(value(first, "DTEND")).isEqualTo("20261013T050000"); // wrap before call: the next morning
        assertThat(value(first, "LOCATION")).isEqualTo("Tom's Diner, 782 Washington Ave, Brooklyn, NY");
        assertThat(value(first, "DESCRIPTION")).contains("Contact: Tom Miller, +1 718 555 0100").contains("Call 19:30, wrap 05:00")
                .contains("/projects/" + project + "/call-sheet");
        assertThat(value(first, "URL")).endsWith("/projects/" + project + "/call-sheet");
        assertThat(value(first, "STATUS")).isEqualTo("CONFIRMED");
        assertThat(value(events.get(1), "DTSTART")).isEqualTo("20261013T193000");
        assertThat(value(first, "UID")).isNotEqualTo(value(events.get(1), "UID"));

        VEvent rooftop = events.get(2);
        assertThat(value(rooftop, "DTSTART")).isEqualTo("20261014");
        assertThat(value(rooftop, "DTEND")).isEqualTo("20261016"); // all day, end exclusive
        assertThat(value(rooftop, "LOCATION")).isEqualTo("Location TBC");
        assertThat(value(rooftop, "STATUS")).isEqualTo("TENTATIVE");
    }

    @Test
    void theFeedCoversEveryActiveProjectThePersonIsOnAndNothingElse() throws Exception {
        Account ada = register("Ada");
        Account eve = register("Eve");
        String mine = project(ada, "Mine");
        scene(ada, mine, 1, "Mine", "2026-11-02", null, null, null);
        String shared = project(eve, "Shared");
        scene(eve, shared, 1, "Shared", "2026-11-03", null, null, null);
        eve.client().post().uri("/api/projects/" + shared + "/members").bodyValue(Map.of("email", ada.email(), "role", "VIEWER"))
                .exchange().expectStatus().is2xxSuccessful();
        scene(eve, project(eve, "Eve's own"), 1, "Not Ada's", "2026-11-04", null, null, null);
        String archived = project(ada, "Archived");
        scene(ada, archived, 1, "Archived", "2026-11-05", null, null, null);
        ada.client().put().uri("/api/projects/" + archived).bodyValue(Map.of("title", "Archived", "status", "ARCHIVED")).exchange().expectStatus().isOk();

        Calendar calendar = parse(feed(feedLink(ada)).expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody());

        List<VEvent> events = calendar.getComponents("VEVENT");
        assertThat(events).extracting(event -> value(event, "SUMMARY")).containsExactly("Mine: Sc. 1 Mine", "Shared: Sc. 1 Shared");
    }

    @Test
    void theLinkIsTheOwnersToMakeAnewOrTurnOffAndABadOneIsNotFound() {
        Account ada = register("Ada");
        ada.client().get().uri("/api/account/calendar-link").exchange().expectStatus().isNotFound();
        String first = feedLink(ada);
        assertThat(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(json(ada.client().get().uri("/api/account/calendar-link").exchange().expectStatus().isOk()).path("token").asText()).isEqualTo(first);
        feed(first).expectStatus().isOk();

        String second = feedLink(ada);
        assertThat(second).isNotEqualTo(first);
        feed(first).expectStatus().isNotFound();
        feed(second).expectStatus().isOk();

        ada.client().delete().uri("/api/account/calendar-link").exchange().expectStatus().isNoContent();
        ada.client().delete().uri("/api/account/calendar-link").exchange().expectStatus().isNoContent();
        feed(second).expectStatus().isNotFound();
        feed("not-a-token").expectStatus().isNotFound();
        web.post().uri("/api/account/calendar-link").exchange().expectStatus().isUnauthorized();
    }
}
