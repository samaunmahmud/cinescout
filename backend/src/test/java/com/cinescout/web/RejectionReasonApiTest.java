package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Why the crew passed on a venue: kept while it stays rejected, gone once it is not. */
class RejectionReasonApiTest extends ApiTest {

    private JsonNode put(Account ada, String venue, Map<String, Object> body) {
        return ada.client().put().uri("/api/locations/" + venue).bodyValue(body).exchange().expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    @Test
    void aReasonIsKeptWhileTheVenueStaysRejectedAndDroppedWhenItIsNot() {
        Account ada = register("Ada");
        String project = ada.client().post().uri("/api/projects").bodyValue(Map.of("title", "Night Shift", "locationArea", "Brooklyn"))
                .exchange().expectBody(JsonNode.class).returnResult().getResponseBody().path("id").asText();
        String scene = ada.client().post().uri("/api/projects/" + project + "/scenes").bodyValue(Map.of("title", "Diner", "sourceText", "INT. DINER"))
                .exchange().expectBody(JsonNode.class).returnResult().getResponseBody().path("id").asText();
        String venue = ada.client().post().uri("/api/scenes/" + scene + "/locations").bodyValue(Map.of("name", "Loud Bar"))
                .exchange().expectBody(JsonNode.class).returnResult().getResponseBody().path("id").asText();

        assertThat(put(ada, venue, Map.of("status", "REJECTED", "rejectionReason", " Too loud ")).path("rejectionReason").asText())
                .isEqualTo("Too loud");
        assertThat(put(ada, venue, Map.of("status", "REJECTED", "notes", "Sound man agrees")).path("rejectionReason").asText())
                .isEqualTo("Too loud");
        assertThat(put(ada, venue, Map.of("status", "SHORTLISTED", "rejectionReason", "ignored")).path("rejectionReason").isNull()).isTrue();
        ada.client().put().uri("/api/locations/" + venue).bodyValue(Map.of("status", "REJECTED", "rejectionReason", "x".repeat(301)))
                .exchange().expectStatus().isBadRequest();
    }
}
