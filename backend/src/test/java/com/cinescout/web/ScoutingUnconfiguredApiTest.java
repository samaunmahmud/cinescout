package com.cinescout.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

/** The default deployment: no AI keys. Everything else works; scouting says plainly that it is not set up. */
class ScoutingUnconfiguredApiTest extends ApiTest {

    @Test
    void scoutingAndParsingAre503WithAnExplanationWhenNoKeysAreConfigured() {
        Account ada = register("Ada");
        String path = "/api/scenes/" + UUID.randomUUID();

        for (String action : new String[] {"/scout", "/parse"}) {
            ada.client().post().uri(path + action).exchange()
                    .expectStatus().isEqualTo(503)
                    .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody().jsonPath("$.detail").isEqualTo("Scouting is not configured on this server");
        }
    }

    @Test
    void theRestOfTheApiWorksWithoutThem() {
        Account ada = register("Ada");

        ada.client().post().uri("/api/projects").bodyValue(Map.of("title", "Neon Nights")).exchange().expectStatus().isCreated();
    }
}
