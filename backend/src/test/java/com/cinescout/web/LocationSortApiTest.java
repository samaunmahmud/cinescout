package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A scene's venues listed in the order asked for, and narrowed to one status. */
class LocationSortApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String venue(Account owner, String sceneId, String name, Integer fit, String status) {
        String id = json(owner.client().post().uri("/api/scenes/" + sceneId + "/locations").bodyValue(Map.of("name", name))
                .exchange().expectStatus().isCreated()).path("id").asText();
        if (fit != null) {
            jdbc.update("UPDATE locations SET fit_score = ? WHERE id = ?::uuid", fit, id);
        }
        owner.client().put().uri("/api/locations/" + id).bodyValue(Map.of("status", status)).exchange().expectStatus().isOk();
        return id;
    }

    private JsonNode list(Account who, String sceneId, String query) {
        return json(who.client().get().uri("/api/scenes/" + sceneId + "/locations" + query).exchange().expectStatus().isOk());
    }

    @Test
    void venuesAreSortedByFitNameNewestOrStatusAndFilteredByStatus() {
        Account ada = register("Ada");
        String project = json(ada.client().post().uri("/api/projects").bodyValue(Map.of("title", "Neon Nights")).exchange()
                .expectStatus().isCreated()).path("id").asText();
        String scene = json(ada.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("title", "Diner", "sourceText", "INT. DINER - NIGHT")).exchange().expectStatus().isCreated()).path("id").asText();
        venue(ada, scene, "bravo Bar", 60, "SHORTLISTED");
        venue(ada, scene, "Alpha Arcade", null, "SUGGESTED");
        venue(ada, scene, "Charlie Cafe", 90, "REJECTED");
        venue(ada, scene, "Delta Diner", 75, "CONFIRMED");

        assertThat(list(ada, scene, "").path("items").findValuesAsText("name"))
                .containsExactly("Charlie Cafe", "Delta Diner", "bravo Bar", "Alpha Arcade");
        assertThat(list(ada, scene, "?sort=FIT&status=SHORTLISTED").path("items").findValuesAsText("name")).containsExactly("bravo Bar");
        assertThat(list(ada, scene, "?sort=NAME").path("items").findValuesAsText("name"))
                .containsExactly("Alpha Arcade", "bravo Bar", "Charlie Cafe", "Delta Diner");
        assertThat(list(ada, scene, "?sort=NEWEST").path("items").findValuesAsText("name"))
                .containsExactly("Delta Diner", "Charlie Cafe", "Alpha Arcade", "bravo Bar");
        assertThat(list(ada, scene, "?sort=STATUS").path("items").findValuesAsText("name"))
                .containsExactly("Delta Diner", "bravo Bar", "Alpha Arcade", "Charlie Cafe");

        JsonNode firstPage = list(ada, scene, "?sort=NAME&page=0&size=2");
        assertThat(firstPage.path("totalItems").asInt()).isEqualTo(4);
        assertThat(list(ada, scene, "?sort=NAME&page=1&size=2").path("items").findValuesAsText("name")).containsExactly("Charlie Cafe", "Delta Diner");
        assertThat(list(ada, scene, "?status=REJECTED").path("totalItems").asInt()).isEqualTo(1);

        ada.client().get().uri("/api/scenes/" + scene + "/locations?sort=PRICE").exchange().expectStatus().isBadRequest();
        register("Mal").client().get().uri("/api/scenes/" + scene + "/locations?sort=NAME").exchange().expectStatus().isNotFound();
    }
}
