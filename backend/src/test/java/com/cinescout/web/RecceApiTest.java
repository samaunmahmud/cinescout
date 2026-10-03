package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The tech recce checklist through the whole application: partial answers, who gave each, checks and roles. */
class RecceApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Night Shift", "locationArea", "London"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String venue(Account member, String projectId) {
        String scene = json(member.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("title", "Pub", "sourceText", "INT. PUB - NIGHT")).exchange().expectStatus().isCreated()).path("id").asText();
        return json(member.client().post().uri("/api/scenes/" + scene + "/locations").bodyValue(Map.of("name", "The Lamb"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private ResponseSpec answer(Account member, String venue, Map<String, Object> answers) {
        return member.client().patch().uri("/api/locations/" + venue + "/recce").bodyValue(answers).exchange();
    }

    @Test
    void answersArriveAFewAtATimeEachSayingWhoGaveItAndWhen() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String project = project(ada);
        ada.client().post().uri("/api/projects/" + project + "/members").bodyValue(Map.of("email", grace.email(), "role", "EDITOR"))
                .exchange().expectStatus().isCreated();
        String venue = venue(ada, project);

        JsonNode first = json(answer(ada, venue, Map.of("sockets", 6, "threePhase", true, "ceilingHeightM", 3.25,
                "stairsOrLift", "LIFT", "powerNotes", "  Board in the cellar  ")).expectStatus().isOk()).path("recce");
        assertThat(first.path("sockets").path("value").asInt()).isEqualTo(6);
        assertThat(first.path("ceilingHeightM").path("value").asDouble()).isEqualTo(3.25);
        assertThat(first.path("powerNotes").path("value").asText()).isEqualTo("Board in the cellar");
        assertThat(first.path("sockets").path("byName").asText()).isEqualTo("Ada");
        assertThat(first.path("sockets").path("at").asText()).isNotEmpty();

        Map<String, Object> second = new HashMap<>();
        second.put("sockets", 6);           // the same answer: still Ada's
        second.put("ceilingHeightM", 3.4);  // a new one: now Grace's
        second.put("powerNotes", null);     // cleared
        second.put("ambientNoise", 2);
        JsonNode recce = json(answer(grace, venue, second).expectStatus().isOk()).path("recce");
        assertThat(recce.path("sockets").path("byName").asText()).isEqualTo("Ada");
        assertThat(recce.path("ceilingHeightM").path("byName").asText()).isEqualTo("Grace");
        assertThat(recce.path("ceilingHeightM").path("value").asDouble()).isEqualTo(3.4);
        assertThat(recce.has("powerNotes")).isFalse();
        assertThat(recce.path("threePhase").path("value").asBoolean()).isTrue();
        assertThat(recce.path("ambientNoise").path("value").asInt()).isEqualTo(2);

        assertThat(json(ada.client().get().uri("/api/locations/" + venue).exchange()).path("recce").path("stairsOrLift").path("value").asText())
                .isEqualTo("LIFT");
    }

    @Test
    void unknownQuestionsAndAnswersOutOfRangeAreRefusedWholesale() {
        Account ada = register("Ada");
        String venue = venue(ada, project(ada));

        answer(ada, venue, Map.of("ambientNoise", 6)).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("ambientNoise");
        answer(ada, venue, Map.of("toilets", true, "helipad", true)).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("helipad");
        answer(ada, venue, Map.of("phoneSignal", "AMAZING")).expectStatus().isBadRequest();
        answer(ada, venue, Map.of("stepFree", "yes")).expectStatus().isBadRequest();
        answer(ada, venue, Map.of("sockets", 2.5)).expectStatus().isBadRequest();
        answer(ada, venue, Map.of("notes", "x".repeat(2001))).expectStatus().isBadRequest();
        assertThat(json(ada.client().get().uri("/api/locations/" + venue).exchange()).path("recce")).isEmpty();
    }

    @Test
    void aViewerReadsTheChecklistAndSomeoneNotOnTheCrewCannotTouchIt() {
        Account ada = register("Ada");
        Account vera = register("Vera");
        Account outsider = register("Mallory");
        String project = project(ada);
        ada.client().post().uri("/api/projects/" + project + "/members").bodyValue(Map.of("email", vera.email(), "role", "VIEWER"))
                .exchange().expectStatus().isCreated();
        String venue = venue(ada, project);
        answer(ada, venue, Map.of("toilets", true)).expectStatus().isOk();

        assertThat(json(vera.client().get().uri("/api/locations/" + venue).exchange()).path("recce").path("toilets").path("value").asBoolean())
                .isTrue();
        answer(vera, venue, Map.of("toilets", false)).expectStatus().isForbidden();
        answer(outsider, venue, Map.of("toilets", false)).expectStatus().isNotFound();
    }
}
