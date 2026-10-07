package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Cover sets: a scene's backup venues, and how the schedule shows them. */
class CoverApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Neon Nights"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String scene(Account owner, String projectId, int number, String start) {
        String id = json(owner.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("sceneNumber", number, "title", "Scene " + number, "sourceText", "EXT. ROOFTOP - DAY"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        Map<String, Object> dates = new HashMap<>();
        dates.put("shootDateStart", start);
        owner.client().put().uri("/api/scenes/" + id + "/shoot-dates").bodyValue(dates).exchange().expectStatus().isOk();
        return id;
    }

    private String venue(Account owner, String sceneId, String name) {
        return json(owner.client().post().uri("/api/scenes/" + sceneId + "/locations")
                .bodyValue(Map.of("name", name, "address", "12 Example Ave, Brooklyn"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private void status(Account owner, String venueId, String status) {
        owner.client().put().uri("/api/locations/" + venueId).bodyValue(Map.of("status", status)).exchange().expectStatus().isOk();
    }

    private ResponseSpec add(Account who, String sceneId, String venueId, String trigger) {
        Map<String, Object> body = new HashMap<>();
        body.put("locationId", venueId);
        body.put("trigger", trigger);
        return who.client().post().uri("/api/scenes/" + sceneId + "/covers").bodyValue(body).exchange();
    }

    private JsonNode schedule(Account who, String projectId) {
        return json(who.client().get().uri("/api/projects/" + projectId + "/schedule").exchange().expectStatus().isOk());
    }

    private void addMember(Account owner, String projectId, Account who, String role) {
        owner.client().post().uri("/api/projects/" + projectId + "/members").bodyValue(Map.of("email", who.email(), "role", role))
                .exchange().expectStatus().is2xxSuccessful();
    }

    @Test
    void aCandidateBecomesACoverWithItsTriggerShowsOnTheScheduleAndIsRemovedAgain() {
        Account ada = register("Ada");
        String project = project(ada);
        String scene = scene(ada, project, 1, "2026-11-02");
        String rooftop = venue(ada, scene, "Skyline Rooftop");
        String warehouse = venue(ada, scene, "Dock Street Warehouse");
        status(ada, rooftop, "CONFIRMED");

        JsonNode cover = json(add(ada, scene, warehouse, "  if rain > 60%  ").expectStatus().isCreated());
        assertThat(cover.path("venueName").asText()).isEqualTo("Dock Street Warehouse");
        assertThat(cover.path("trigger").asText()).isEqualTo("if rain > 60%");
        assertThat(cover.path("addedByName").asText()).isEqualTo("Ada");
        assertThat(cover.path("createdAt").isNull()).isFalse();
        String coverId = cover.path("id").asText();

        JsonNode entry = schedule(ada, project).path("days").get(0).path("scenes").get(0);
        assertThat(entry.path("venues").findValuesAsText("name")).containsExactly("Skyline Rooftop");
        assertThat(entry.path("covers")).hasSize(1);
        assertThat(entry.path("covers").get(0).path("name").asText()).isEqualTo("Dock Street Warehouse");
        assertThat(entry.path("covers").get(0).path("trigger").asText()).isEqualTo("if rain > 60%");

        JsonNode changed = json(ada.client().put().uri("/api/covers/" + coverId).bodyValue(Map.of("trigger", " ")).exchange().expectStatus().isOk());
        assertThat(changed.path("trigger").isNull()).isTrue();

        JsonNode list = json(ada.client().get().uri("/api/scenes/" + scene + "/covers").exchange().expectStatus().isOk());
        assertThat(list.path("items").findValuesAsText("locationId")).containsExactly(warehouse);

        ada.client().delete().uri("/api/covers/" + coverId).exchange().expectStatus().isNoContent();
        assertThat(json(ada.client().get().uri("/api/scenes/" + scene + "/covers").exchange().expectStatus().isOk()).path("items")).isEmpty();
        ada.client().get().uri("/api/locations/" + warehouse).exchange().expectStatus().isOk(); // still a candidate

        JsonNode activity = json(ada.client().get().uri("/api/projects/" + project + "/activity?kind=SCHEDULE").exchange().expectStatus().isOk());
        assertThat(activity.path("items").findValuesAsText("verb")).contains("COVER_SET_CHANGED");
    }

    @Test
    void aCoverIsOneOfTheScenesOwnCandidatesNotItsConfirmedVenueAndOnlyOnce() {
        Account ada = register("Ada");
        String project = project(ada);
        String scene = scene(ada, project, 1, null);
        String other = scene(ada, project, 2, null);
        String rooftop = venue(ada, scene, "Skyline Rooftop");
        String elsewhere = venue(ada, other, "Other Scene's Venue");

        JsonNode foreign = json(add(ada, scene, elsewhere, null).expectStatus().isBadRequest());
        assertThat(foreign.toString()).contains("locationId").contains("this scene's candidate");
        assertThat(json(add(ada, scene, null, null).expectStatus().isBadRequest()).toString()).contains("locationId");
        add(ada, scene, rooftop, "x".repeat(201)).expectStatus().isBadRequest();

        add(ada, scene, rooftop, null).expectStatus().isCreated();
        assertThat(json(add(ada, scene, rooftop, null).expectStatus().isEqualTo(409)).path("detail").asText())
                .isEqualTo("Skyline Rooftop is already a cover set for this scene");

        String confirmed = venue(ada, scene, "The Booked One");
        status(ada, confirmed, "CONFIRMED");
        assertThat(json(add(ada, scene, confirmed, null).expectStatus().isEqualTo(409)).path("detail").asText())
                .contains("is confirmed for this scene");

        for (int i = 2; i <= 5; i++) {
            add(ada, scene, venue(ada, scene, "Backup " + i), null).expectStatus().isCreated();
        }
        assertThat(json(add(ada, scene, venue(ada, scene, "One Too Many"), null).expectStatus().isEqualTo(409)).path("detail").asText())
                .contains("at most 5");
    }

    @Test
    void aCoverSinceConfirmedLeavesTheScheduleAndDeletingTheVenueDeletesTheCover() {
        Account ada = register("Ada");
        String project = project(ada);
        String scene = scene(ada, project, 1, "2026-11-02");
        String warehouse = venue(ada, scene, "Dock Street Warehouse");
        add(ada, scene, warehouse, "if rain").expectStatus().isCreated();

        status(ada, warehouse, "CONFIRMED");
        JsonNode entry = schedule(ada, project).path("days").get(0).path("scenes").get(0);
        assertThat(entry.path("venues").findValuesAsText("name")).containsExactly("Dock Street Warehouse");
        assertThat(entry.path("covers")).isEmpty();

        ada.client().delete().uri("/api/locations/" + warehouse).exchange().expectStatus().isNoContent();
        assertThat(json(ada.client().get().uri("/api/scenes/" + scene + "/covers").exchange().expectStatus().isOk()).path("items")).isEmpty();
    }

    @Test
    void viewersReadEditorsChangeAndOutsidersSeeNothing() {
        Account ada = register("Ada");
        Account vic = register("Vic");
        Account eve = register("Eve");
        Account mal = register("Mal");
        String project = project(ada);
        addMember(ada, project, vic, "VIEWER");
        addMember(ada, project, eve, "EDITOR");
        String scene = scene(ada, project, 1, null);
        String coverId = json(add(ada, scene, venue(ada, scene, "Dock Street Warehouse"), "if rain").expectStatus().isCreated()).path("id").asText();
        String spare = venue(ada, scene, "Spare");

        vic.client().get().uri("/api/scenes/" + scene + "/covers").exchange().expectStatus().isOk();
        add(vic, scene, spare, null).expectStatus().isForbidden();
        vic.client().put().uri("/api/covers/" + coverId).bodyValue(Map.of("trigger", "now")).exchange().expectStatus().isForbidden();
        vic.client().delete().uri("/api/covers/" + coverId).exchange().expectStatus().isForbidden();

        eve.client().put().uri("/api/covers/" + coverId).bodyValue(Map.of("trigger", "if wind > 40 km/h")).exchange().expectStatus().isOk();
        add(eve, scene, spare, null).expectStatus().isCreated();

        mal.client().get().uri("/api/scenes/" + scene + "/covers").exchange().expectStatus().isNotFound();
        add(mal, scene, spare, null).expectStatus().isNotFound();
        mal.client().put().uri("/api/covers/" + coverId).bodyValue(Map.of("trigger", "now")).exchange().expectStatus().isNotFound();
        mal.client().delete().uri("/api/covers/" + coverId).exchange().expectStatus().isNotFound();
        ada.client().delete().uri("/api/covers/" + coverId).exchange().expectStatus().isNoContent();
    }
}
