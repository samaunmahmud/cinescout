package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Sharing a call sheet by link, through the whole application. */
class CallSheetShareApiTest extends ApiTest {

    private JsonNode json(org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects")
                .bodyValue(Map.of("title", "Night Shift", "description", "PRIVATE-DESCRIPTION", "locationArea", "Brooklyn"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private void datedScene(Account owner, String projectId) {
        owner.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("sceneNumber", 1, "title", "INT. DINER - NIGHT", "sourceText", "PRIVATE-SCRIPT", "shootDateStart", "2026-10-12"))
                .exchange().expectStatus().isCreated();
    }

    @Test
    void aSharedCallSheetCanBeReadWithoutAnAccountAndShowsOnlyTheSheet() {
        Account ada = register("Ada");
        String project = project(ada);
        datedScene(ada, project);
        ada.client().get().uri("/api/projects/" + project + "/call-sheet-link").exchange().expectStatus().isNotFound();

        String token = json(ada.client().post().uri("/api/projects/" + project + "/call-sheet-link").exchange().expectStatus().isOk())
                .path("token").asText();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(json(ada.client().get().uri("/api/projects/" + project + "/call-sheet-link").exchange().expectStatus().isOk())
                .path("token").asText()).isEqualTo(token);

        var response = web.get().uri("/api/public/call-sheets/" + token).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Robots-Tag", "noindex, nofollow")
                .expectHeader().cacheControl(org.springframework.http.CacheControl.noStore())
                .expectBody(JsonNode.class).returnResult().getResponseBody();
        assertThat(response.path("projectTitle").asText()).isEqualTo("Night Shift");
        assertThat(response.path("locationArea").asText()).isEqualTo("Brooklyn");
        assertThat(response.path("preparedBy").asText()).isEqualTo("Ada");
        assertThat(response.path("schedule").path("days").get(0).path("scenes").get(0).path("title").asText()).isEqualTo("INT. DINER - NIGHT");
        assertThat(response.toString()).doesNotContain("PRIVATE-DESCRIPTION").doesNotContain("PRIVATE-SCRIPT").doesNotContain(ada.email());
    }

    @Test
    void aNewLinkReplacesTheOldAndStoppingSharingCutsItOff() {
        Account ada = register("Ada");
        String project = project(ada);
        String first = json(ada.client().post().uri("/api/projects/" + project + "/call-sheet-link").exchange().expectStatus().isOk()).path("token").asText();
        String second = json(ada.client().post().uri("/api/projects/" + project + "/call-sheet-link").exchange().expectStatus().isOk()).path("token").asText();

        assertThat(second).isNotEqualTo(first);
        web.get().uri("/api/public/call-sheets/" + first).exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.detail").isEqualTo("This call sheet is not shared, or no longer is");
        web.get().uri("/api/public/call-sheets/" + second).exchange().expectStatus().isOk();

        ada.client().delete().uri("/api/projects/" + project + "/call-sheet-link").exchange().expectStatus().isNoContent();
        ada.client().delete().uri("/api/projects/" + project + "/call-sheet-link").exchange().expectStatus().isNoContent();
        web.get().uri("/api/public/call-sheets/" + second).exchange().expectStatus().isNotFound();
    }

    @Test
    void onlyTheOwnerCanShareAndAMadeUpTokenIsNotFound() {
        Account ada = register("Ada");
        String project = project(ada);
        Account grace = register("Grace");

        grace.client().post().uri("/api/projects/" + project + "/call-sheet-link").exchange().expectStatus().isNotFound();
        grace.client().get().uri("/api/projects/" + project + "/call-sheet-link").exchange().expectStatus().isNotFound();
        grace.client().delete().uri("/api/projects/" + project + "/call-sheet-link").exchange().expectStatus().isNotFound();
        web.post().uri("/api/projects/" + project + "/call-sheet-link").exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/public/call-sheets/" + "A".repeat(43)).exchange().expectStatus().isNotFound();
        web.get().uri("/api/public/call-sheets/short").exchange().expectStatus().isNotFound();
        web.get().uri("/api/public/other").exchange().expectStatus().isUnauthorized();
    }
}
