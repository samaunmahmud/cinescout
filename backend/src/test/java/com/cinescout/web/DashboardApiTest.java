package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The overview across a person's active productions, and nothing from anyone else's. */
class DashboardApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner, String title) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", title)).exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String scene(Account owner, String project, String title, LocalDate day) {
        String scene = json(owner.client().post().uri("/api/projects/" + project + "/scenes").bodyValue(Map.of("title", title, "sourceText", title))
                .exchange().expectStatus().isCreated()).path("id").asText();
        owner.client().put().uri("/api/scenes/" + scene + "/shoot-dates").bodyValue(Map.of("shootDateStart", day.toString(), "callTime", "07:30"))
                .exchange().expectStatus().isOk();
        return scene;
    }

    private String venue(Account owner, String scene, String name, String status, Integer fit) {
        String venue = json(owner.client().post().uri("/api/scenes/" + scene + "/locations").bodyValue(Map.of("name", name))
                .exchange().expectStatus().isCreated()).path("id").asText();
        if (!status.equals("SUGGESTED")) {
            owner.client().put().uri("/api/locations/" + venue).bodyValue(Map.of("status", status)).exchange().expectStatus().isOk();
        }
        if (fit != null) {
            jdbc.update("UPDATE locations SET fit_score = ? WHERE id = ?::uuid", fit, venue);
        }
        return venue;
    }

    @Test
    void numbersNextShootDaysFreshFindsAndActivityAcrossTheirProductionsOnly() {
        Account ada = register("Ada");
        Account mal = register("Mal");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String ferry = project(ada, "The Night Ferry");
        String terminal = scene(ada, ferry, "Ferry terminal", today.plusDays(3));
        scene(ada, ferry, "Ferry deck", today.plusDays(1));
        scene(ada, ferry, "Old flashback", today.minusDays(5));
        venue(ada, terminal, "Tannery Hall", "CONFIRMED", 82);
        venue(ada, terminal, "Old Quay Pier", "SHORTLISTED", 76);
        venue(ada, terminal, "Copperline Warehouse", "SUGGESTED", 61);
        venue(ada, terminal, "Dockside Depot", "SUGGESTED", 90);
        venue(ada, terminal, "Unscored", "SUGGESTED", null);
        String malsScene = scene(mal, project(mal, "Elsewhere"), "Not Ada's", today.plusDays(2));
        venue(mal, malsScene, "Mal's venue", "SUGGESTED", 99);

        JsonNode dashboard = json(ada.client().get().uri("/api/dashboard").exchange().expectStatus().isOk());

        assertThat(dashboard.path("totals").path("productions").asLong()).isEqualTo(1);
        assertThat(dashboard.path("totals").path("scenes").asLong()).isEqualTo(3);
        assertThat(dashboard.path("totals").path("lockedScenes").asLong()).isEqualTo(1);
        assertThat(dashboard.path("totals").path("venuesInPlay").asLong()).isEqualTo(2);
        assertThat(dashboard.path("upcoming").findValuesAsText("sceneTitle")).containsExactly("Ferry deck", "Ferry terminal");
        assertThat(dashboard.path("upcoming").get(1).path("venueName").asText()).isEqualTo("Tannery Hall");
        assertThat(dashboard.path("upcoming").get(1).path("callTime").asText()).isEqualTo("07:30:00");
        assertThat(dashboard.path("freshFinds").findValuesAsText("name")).containsExactly("Dockside Depot", "Copperline Warehouse");
        assertThat(dashboard.path("activity")).isNotEmpty();
        assertThat(dashboard.path("activity").get(0).path("projectTitle").asText()).isEqualTo("The Night Ferry");
        assertThat(dashboard.toString()).doesNotContain("Mal's venue", "Not Ada's", "Elsewhere");

        JsonNode empty = json(register("Nina").client().get().uri("/api/dashboard").exchange().expectStatus().isOk());
        assertThat(empty.path("totals").path("productions").asLong()).isZero();
        assertThat(empty.path("upcoming")).isEmpty();
        web.get().uri("/api/dashboard").exchange().expectStatus().isUnauthorized();
    }
}
