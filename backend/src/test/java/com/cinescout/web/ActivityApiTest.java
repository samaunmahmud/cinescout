package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A project's activity log, through the whole application: what is recorded, the filter, who may read it. */
class ActivityApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Night Shift", "locationArea", "Brooklyn"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String scene(Account member, String projectId) {
        return json(member.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("title", "Diner", "sourceText", "PRIVATE-SCRIPT"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String venue(Account member, String sceneId) {
        return json(member.client().post().uri("/api/scenes/" + sceneId + "/locations").bodyValue(Map.of("name", "Moonlight Diner"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private JsonNode activity(Account member, String projectId, String query) {
        return json(member.client().get().uri("/api/projects/" + projectId + "/activity" + query).exchange().expectStatus().isOk());
    }

    private static List<String> verbs(JsonNode page) {
        List<String> verbs = new ArrayList<>();
        page.path("items").forEach(line -> verbs.add(line.path("verb").asText()));
        return verbs;
    }

    @Test
    void theWorkOnAProjectIsLoggedNewestFirstAndCanBeFilteredByKind() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String project = project(ada);
        String scene = scene(ada, project);
        String venue = venue(ada, scene);

        ada.client().post().uri("/api/projects/" + project + "/members").bodyValue(Map.of("email", grace.email(), "role", "VIEWER"))
                .exchange().expectStatus().isCreated();
        ada.client().put().uri("/api/projects/" + project + "/members/" + grace.id()).bodyValue(Map.of("role", "EDITOR"))
                .exchange().expectStatus().isOk();
        grace.client().put().uri("/api/locations/" + venue).bodyValue(Map.of("status", "SHORTLISTED", "notes", "PRIVATE-NOTE"))
                .exchange().expectStatus().isOk();
        // Saving the notes again without a new status is not news.
        grace.client().put().uri("/api/locations/" + venue).bodyValue(Map.of("status", "SHORTLISTED", "notes", "PRIVATE-NOTE 2"))
                .exchange().expectStatus().isOk();
        ada.client().put().uri("/api/scenes/" + scene + "/shoot-dates").bodyValue(Map.of("shootDateStart", "2026-11-02"))
                .exchange().expectStatus().isOk();
        ada.client().post().uri("/api/locations/" + venue + "/comments").bodyValue(Map.of("body", "PRIVATE-COMMENT"))
                .exchange().expectStatus().isCreated();
        String token = json(ada.client().post().uri("/api/projects/" + project + "/director-link").exchange().expectStatus().isOk())
                .path("token").asText();
        web.post().uri("/api/public/shortlists/" + token + "/venues/" + venue + "/response")
                .bodyValue(Map.of("guestName", "Wes", "verdict", "APPROVE")).exchange().expectStatus().isOk();
        grace.client().delete().uri("/api/projects/" + project + "/members/" + grace.id()).exchange().expectStatus().isNoContent();

        JsonNode all = activity(ada, project, "");
        assertThat(verbs(all)).containsExactly("MEMBER_LEFT", "DIRECTOR_CALLED", "COMMENTED", "SHOOT_DATES_CHANGED",
                "VENUE_STATUS_CHANGED", "ROLE_CHANGED", "MEMBER_JOINED");
        String raw = ada.client().get().uri("/api/projects/" + project + "/activity").exchange().expectBody(String.class)
                .returnResult().getResponseBody();
        assertThat(raw).doesNotContain("PRIVATE-NOTE", "PRIVATE-COMMENT", "PRIVATE-SCRIPT");

        JsonNode status = all.path("items").get(4);
        assertThat(status.path("kind").asText()).isEqualTo("VENUE");
        assertThat(status.path("actorName").asText()).isEqualTo("Grace");
        assertThat(status.path("targetType").asText()).isEqualTo("LOCATION");
        assertThat(status.path("targetId").asText()).isEqualTo(venue);
        assertThat(status.path("payload").path("venue").asText()).isEqualTo("Moonlight Diner");
        assertThat(status.path("payload").path("from").asText()).isEqualTo("SUGGESTED");
        assertThat(status.path("payload").path("to").asText()).isEqualTo("SHORTLISTED");

        JsonNode guest = all.path("items").get(1);
        assertThat(guest.path("actorName").asText()).isEqualTo("Wes");
        assertThat(guest.path("actorId").isNull()).isTrue();
        assertThat(guest.path("payload").path("guest").asBoolean()).isTrue();
        assertThat(guest.path("payload").path("verdict").asText()).isEqualTo("APPROVE");

        JsonNode dates = all.path("items").get(3).path("payload");
        assertThat(dates.path("fromStart").isNull()).isTrue();
        assertThat(dates.path("toStart").asText()).isEqualTo("2026-11-02");

        assertThat(verbs(activity(ada, project, "?kind=CREW"))).containsExactly("MEMBER_LEFT", "ROLE_CHANGED", "MEMBER_JOINED");
        JsonNode paged = activity(ada, project, "?page=1&size=2");
        assertThat(paged.path("totalItems").asInt()).isEqualTo(7);
        assertThat(verbs(paged)).containsExactly("COMMENTED", "SHOOT_DATES_CHANGED");
        ada.client().get().uri("/api/projects/" + project + "/activity?kind=GOSSIP").exchange().expectStatus().isBadRequest();
    }

    @Test
    void handingOverAndRemovingAreLoggedAndTheLogSurvivesAnAccountGoing() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        Account hedy = register("Hedy");
        String project = project(ada);
        for (Account member : new Account[] {grace, hedy}) {
            ada.client().post().uri("/api/projects/" + project + "/members").bodyValue(Map.of("email", member.email(), "role", "EDITOR"))
                    .exchange().expectStatus().isCreated();
        }
        ada.client().post().uri("/api/projects/" + project + "/transfer").bodyValue(Map.of("userId", grace.id()))
                .exchange().expectStatus().isOk();
        grace.client().delete().uri("/api/projects/" + project + "/members/" + hedy.id()).exchange().expectStatus().isNoContent();
        jdbc.update("DELETE FROM users WHERE id = ?", ada.id());

        JsonNode crew = activity(grace, project, "?kind=CREW");
        assertThat(verbs(crew)).containsExactly("MEMBER_REMOVED", "OWNERSHIP_TRANSFERRED", "MEMBER_JOINED", "MEMBER_JOINED");
        JsonNode handover = crew.path("items").get(1);
        assertThat(handover.path("actorId").isNull()).isTrue();
        assertThat(handover.path("actorName").asText()).isEqualTo("Ada");
        assertThat(handover.path("payload").path("member").asText()).isEqualTo("Grace");
    }

    @Test
    void onlyTheCrewReadsTheLogAndNoOneCanRewriteIt() {
        Account ada = register("Ada");
        Account outsider = register("Mallory");
        String project = project(ada);
        venue(ada, scene(ada, project));
        String scene = scene(ada, project);
        ada.client().put().uri("/api/scenes/" + scene + "/shoot-dates").bodyValue(Map.of("shootDateStart", "2026-11-02"))
                .exchange().expectStatus().isOk();

        outsider.client().get().uri("/api/projects/" + project + "/activity").exchange().expectStatus().isNotFound();
        assertThatThrownBy(() -> jdbc.update("UPDATE activity SET actor_name = 'Mallory'"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }
}
