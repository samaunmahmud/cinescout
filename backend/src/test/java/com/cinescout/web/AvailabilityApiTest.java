package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Holds and availability on a venue's days, and the conflicts the schedule finds with them. */
class AvailabilityApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Neon Nights"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String scene(Account owner, String projectId, int number, String start, String end, String call, String wrap) {
        String id = json(owner.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("sceneNumber", number, "title", "Scene " + number, "sourceText", "INT. DINER - NIGHT"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        Map<String, Object> dates = new HashMap<>();
        dates.put("shootDateStart", start);
        dates.put("shootDateEnd", end);
        dates.put("callTime", call);
        dates.put("wrapTime", wrap);
        owner.client().put().uri("/api/scenes/" + id + "/shoot-dates").bodyValue(dates).exchange().expectStatus().isOk();
        return id;
    }

    private String venue(Account owner, String sceneId, String name, boolean confirmed) {
        String id = json(owner.client().post().uri("/api/scenes/" + sceneId + "/locations")
                .bodyValue(Map.of("name", name, "address", "12 Example Ave, Brooklyn"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        if (confirmed) {
            owner.client().put().uri("/api/locations/" + id).bodyValue(Map.of("status", "CONFIRMED")).exchange().expectStatus().isOk();
        }
        return id;
    }

    private ResponseSpec set(Account who, String venueId, Map<String, ?> body) {
        return who.client().put().uri("/api/locations/" + venueId + "/availability").bodyValue(body).exchange();
    }

    private JsonNode schedule(Account who, String projectId) {
        return json(who.client().get().uri("/api/projects/" + projectId + "/schedule").exchange().expectStatus().isOk());
    }

    private void addMember(Account owner, String projectId, Account who, String role) {
        owner.client().post().uri("/api/projects/" + projectId + "/members").bodyValue(Map.of("email", who.email(), "role", role))
                .exchange().expectStatus().is2xxSuccessful();
    }

    @Test
    void aRunOfDaysIsSetListedEarliestFirstReplacedAndForgotten() {
        Account ada = register("Ada");
        String project = project(ada);
        String venue = venue(ada, scene(ada, project, 1, "2026-11-02", null, null, null), "Starlite Diner", false);

        JsonNode saved = json(set(ada, venue, Map.of("from", "2026-11-02", "to", "2026-11-04", "state", "HELD",
                "holdExpiresOn", "2026-10-28", "note", " Held by Jo ")).expectStatus().isOk());
        assertThat(saved.findValuesAsText("day")).containsExactly("2026-11-02", "2026-11-03", "2026-11-04");
        assertThat(saved.get(0).path("note").asText()).isEqualTo("Held by Jo");
        assertThat(saved.get(0).path("setByName").asText()).isEqualTo("Ada");

        set(ada, venue, Map.of("from", "2026-11-03", "state", "CONFIRMED")).expectStatus().isOk();
        ada.client().delete().uri("/api/locations/" + venue + "/availability/2026-11-04").exchange().expectStatus().isNoContent();
        ada.client().delete().uri("/api/locations/" + venue + "/availability/2026-12-25").exchange().expectStatus().isNoContent();

        JsonNode list = json(ada.client().get().uri("/api/locations/" + venue + "/availability").exchange().expectStatus().isOk());
        assertThat(list.path("items").findValuesAsText("state")).containsExactly("HELD", "CONFIRMED");
        assertThat(list.path("items").get(1).path("holdExpiresOn").isNull()).isTrue();
        assertThat(list.path("items").get(1).path("note").isNull()).isTrue();

        JsonNode activity = json(ada.client().get().uri("/api/projects/" + project + "/activity?kind=SCHEDULE").exchange().expectStatus().isOk());
        assertThat(activity.path("items").findValuesAsText("verb")).contains("AVAILABILITY_CHANGED");
    }

    @Test
    void badRequestsAreRefusedPlainly() {
        Account ada = register("Ada");
        String venue = venue(ada, scene(ada, project(ada), 1, null, null, null, null), "Starlite Diner", false);

        JsonNode expiry = json(set(ada, venue, Map.of("from", "2026-11-02", "state", "CONFIRMED", "holdExpiresOn", "2026-10-28"))
                .expectStatus().isBadRequest());
        assertThat(expiry.toString()).contains("only a pencil or a hold can have an expiry date");
        set(ada, venue, Map.of("from", "2026-11-02", "to", "2026-11-01", "state", "HELD")).expectStatus().isBadRequest();
        set(ada, venue, Map.of("from", "2026-01-01", "to", "2026-12-31", "state", "HELD")).expectStatus().isBadRequest();
        set(ada, venue, Map.of("from", "2026-11-02", "state", "BOOKED")).expectStatus().isBadRequest();
        set(ada, venue, Map.of("state", "HELD")).expectStatus().isBadRequest();
    }

    @Test
    void viewersReadAndOutsidersSeeNothing() {
        Account ada = register("Ada");
        Account vera = register("Vera");
        Account mallory = register("Mallory");
        String project = project(ada);
        addMember(ada, project, vera, "VIEWER");
        String venue = venue(ada, scene(ada, project, 1, null, null, null, null), "Starlite Diner", false);
        set(ada, venue, Map.of("from", "2026-11-02", "state", "PENCILLED")).expectStatus().isOk();

        vera.client().get().uri("/api/locations/" + venue + "/availability").exchange().expectStatus().isOk();
        set(vera, venue, Map.of("from", "2026-11-02", "state", "HELD")).expectStatus().isForbidden();
        vera.client().delete().uri("/api/locations/" + venue + "/availability/2026-11-02").exchange().expectStatus().isForbidden();

        mallory.client().get().uri("/api/locations/" + venue + "/availability").exchange().expectStatus().isNotFound();
        set(mallory, venue, Map.of("from", "2026-11-02", "state", "HELD")).expectStatus().isNotFound();
        mallory.client().delete().uri("/api/locations/" + venue + "/availability/2026-11-02").exchange().expectStatus().isNotFound();
    }

    @Test
    void theScheduleShowsEachVenuesStateAndWhereItClashes() {
        Account ada = register("Ada");
        String project = project(ada);
        String four = scene(ada, project, 4, "2026-11-02", "2026-11-03", null, null);
        String diner = venue(ada, four, "Starlite Diner", true);
        String seven = scene(ada, project, 7, "2026-11-05", null, null, null);
        String cafe = venue(ada, seven, "Neon Spoon Cafe", true);
        set(ada, diner, Map.of("from", "2026-11-02", "state", "HELD", "holdExpiresOn", "2026-10-28")).expectStatus().isOk();
        set(ada, diner, Map.of("from", "2026-11-03", "state", "UNAVAILABLE")).expectStatus().isOk();
        set(ada, cafe, Map.of("from", "2026-11-05", "state", "CONFIRMED")).expectStatus().isOk();

        JsonNode schedule = schedule(ada, project);

        JsonNode dinerEntry = schedule.path("days").get(0).path("scenes").get(0).path("venues").get(0);
        assertThat(dinerEntry.path("booking").path("state").asText()).isEqualTo("HELD");
        assertThat(dinerEntry.path("booking").path("holdExpiresOn").asText()).isEqualTo("2026-10-28");
        assertThat(schedule.path("days").get(1).path("scenes").get(0).path("venues").get(0).path("booking").path("state").asText())
                .isEqualTo("CONFIRMED");
        JsonNode conflicts = schedule.path("conflicts");
        assertThat(conflicts.findValuesAsText("kind")).containsExactly("HOLD_EXPIRES", "UNAVAILABLE");
        assertThat(conflicts.get(0).path("problem").asBoolean()).isTrue();
        assertThat(conflicts.get(0).path("message").asText())
                .isEqualTo("The hold on Starlite Diner for Mon 2 Nov lapses on Wed 28 Oct, before the shoot. Renew it or confirm the booking.");
        assertThat(conflicts.get(1).path("message").asText()).isEqualTo("Starlite Diner is marked unavailable on Tue 3 Nov, a shoot day of scene 4.");
        assertThat(conflicts.get(1).path("sceneIds").get(0).asText()).isEqualTo(four);
        assertThat(conflicts.get(1).path("locationId").asText()).isEqualTo(diner);
    }

    @Test
    void aVenueMarkedUnavailableOnAnotherScenesRowStillCounts() {
        Account ada = register("Ada");
        String project = project(ada);
        String first = scene(ada, project, 1, "2026-11-02", null, null, null);
        String candidate = venue(ada, first, "The Starlite Diner", false);
        venue(ada, scene(ada, project, 2, "2026-11-02", null, null, null), "Starlite Diner", true);
        set(ada, candidate, Map.of("from", "2026-11-02", "state", "UNAVAILABLE")).expectStatus().isOk();

        assertThat(schedule(ada, project).path("conflicts").findValuesAsText("kind")).containsExactly("UNAVAILABLE");
    }

    @Test
    void oneHoldSeenFromTwoScenesAtTheVenueIsOneConflictNamingBoth() {
        Account ada = register("Ada");
        String project = project(ada);
        String first = scene(ada, project, 1, "2026-11-02", null, "07:00", "11:00");
        String diner = venue(ada, first, "Starlite Diner", true);
        String second = scene(ada, project, 2, "2026-11-02", null, "13:00", "18:00");
        venue(ada, second, "Starlite Diner", true);
        set(ada, diner, Map.of("from", "2026-11-02", "state", "PENCILLED", "holdExpiresOn", "2026-10-20")).expectStatus().isOk();

        JsonNode conflicts = schedule(ada, project).path("conflicts");

        assertThat(conflicts.size()).isEqualTo(1);
        assertThat(conflicts.get(0).path("message").asText()).startsWith("The pencil on Starlite Diner for Mon 2 Nov lapses on Tue 20 Oct");
        assertThat(conflicts.get(0).path("sceneIds").size()).isEqualTo(2);
    }

    @Test
    void twoScenesAtOneVenueOnOneDayAreAWarningUnlessTheirTimesKeepThemApart() {
        Account ada = register("Ada");
        String project = project(ada);
        String morning = scene(ada, project, 1, "2026-11-02", null, "07:00", "12:00");
        venue(ada, morning, "Starlite Diner", true);
        String afternoon = scene(ada, project, 2, "2026-11-02", null, "13:00", "18:00");
        venue(ada, afternoon, "Starlite Diner", true);
        assertThat(schedule(ada, project).path("conflicts").size()).isZero();

        // A night shoot runs past midnight, so it overlaps an evening scene.
        String night = scene(ada, project, 3, "2026-11-02", null, "17:00", "02:00");
        venue(ada, night, "Starlite Diner", true);
        JsonNode conflicts = schedule(ada, project).path("conflicts");
        assertThat(conflicts.size()).isEqualTo(1);
        assertThat(conflicts.get(0).path("kind").asText()).isEqualTo("DOUBLE_BOOKED");
        assertThat(conflicts.get(0).path("problem").asBoolean()).isFalse();
        assertThat(conflicts.get(0).path("message").asText())
                .isEqualTo("Scene 2 and scene 3 are both at Starlite Diner on Mon 2 Nov, at overlapping times (13:00–18:00 and 17:00–02:00).");

        String untimed = scene(ada, project, 4, "2026-11-02", null, null, null);
        venue(ada, untimed, "Elsewhere Bar", true);
        venue(ada, scene(ada, project, 5, "2026-11-02", null, null, null), "Elsewhere Bar", true);
        assertThat(schedule(ada, project).path("conflicts").findValuesAsText("message"))
                .contains("Scene 4 and scene 5 are both at Elsewhere Bar on Mon 2 Nov; set their call and wrap times to check they do not overlap.");
    }

    @Test
    void theSharedCallSheetCarriesNoHoldsOrConflictsButTheTimes() {
        Account ada = register("Ada");
        String project = project(ada);
        String scene = scene(ada, project, 1, "2026-11-02", null, "07:30", "16:00");
        String diner = venue(ada, scene, "Starlite Diner", true);
        set(ada, diner, Map.of("from", "2026-11-02", "state", "UNAVAILABLE", "note", "PRIVATE-NOTE")).expectStatus().isOk();
        String token = json(ada.client().post().uri("/api/projects/" + project + "/call-sheet-link").exchange().expectStatus().is2xxSuccessful())
                .path("token").asText();

        JsonNode sheet = json(web.get().uri("/api/public/call-sheets/" + token).exchange().expectStatus().isOk());

        JsonNode entry = sheet.path("schedule").path("days").get(0).path("scenes").get(0);
        assertThat(entry.path("callTime").asText()).startsWith("07:30");
        assertThat(entry.path("venues").get(0).path("booking").isNull()).isTrue();
        assertThat(sheet.path("schedule").path("conflicts").size()).isZero();
        assertThat(sheet.toString()).doesNotContain("PRIVATE-NOTE", "UNAVAILABLE");
    }
}
