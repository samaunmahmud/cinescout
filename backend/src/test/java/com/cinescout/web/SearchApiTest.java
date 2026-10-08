package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The command palette's search: only the person's own projects, starts-with first, wildcards taken literally. */
class SearchApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private JsonNode search(Account who, String q) {
        return json(who.client().get().uri(uri -> uri.path("/api/search").queryParam("q", q).build()).exchange().expectStatus().isOk());
    }

    @Test
    void findsProjectsScenesAndVenuesOnlyInTheirOwnProjects() {
        Account ada = register("Ada");
        Account mal = register("Mal");
        String project = json(ada.client().post().uri("/api/projects").bodyValue(Map.of("title", "Harbour Lights", "locationArea", "Dockside, London"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        String scene = json(ada.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("sceneNumber", 4, "title", "EXT. DOCK - NIGHT", "sourceText", "EXT. DOCK - NIGHT")).exchange().expectStatus().isCreated()).path("id").asText();
        ada.client().post().uri("/api/scenes/" + scene + "/locations").bodyValue(Map.of("name", "Old Dock Inn", "address", "1 Quay St"))
                .exchange().expectStatus().isCreated();
        ada.client().post().uri("/api/scenes/" + scene + "/locations").bodyValue(Map.of("name", "Riverside Hall", "address", "9 Dockyard Rd"))
                .exchange().expectStatus().isCreated();
        String other = json(mal.client().post().uri("/api/projects").bodyValue(Map.of("title", "Dock Strike")).exchange().expectStatus().isCreated()).path("id").asText();

        JsonNode found = search(ada, "  DOCK ");
        assertThat(found.path("projects").findValuesAsText("title")).containsExactly("Harbour Lights"); // by its area
        assertThat(found.path("scenes").get(0).path("projectTitle").asText()).isEqualTo("Harbour Lights");
        assertThat(found.path("scenes").get(0).path("sceneNumber").asInt()).isEqualTo(4);
        assertThat(found.path("venues").findValuesAsText("name")).containsExactly("Old Dock Inn", "Riverside Hall"); // name first, then address
        assertThat(found.path("venues").get(0).path("status").asText()).isEqualTo("SUGGESTED");
        assertThat(found.toString()).doesNotContain(other);

        assertThat(search(ada, "d").path("venues")).isEmpty(); // too short
        assertThat(search(ada, "50%").path("venues")).isEmpty(); // % is not a wildcard
        web.get().uri("/api/search?q=dock").exchange().expectStatus().isUnauthorized();
    }
}
