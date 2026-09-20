package com.cinescout.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

/** The default deployment: no AI keys. Drafts can be managed, but nothing can be generated, and it says so. */
class OutreachUnconfiguredApiTest extends ApiTest {

    @Test
    void generatingIs503WithAnExplanationWhenNoKeyIsConfigured() {
        Account ada = register("Ada");

        ada.client().post().uri("/api/locations/" + UUID.randomUUID() + "/outreach-drafts/generate").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.detail").isEqualTo("Outreach generation is not configured on this server");
    }

    @Test
    void theRestOfOutreachStillWorks() {
        Account ada = register("Ada");

        ada.client().get().uri("/api/outreach-drafts/" + UUID.randomUUID()).exchange().expectStatus().isNotFound();
        ada.client().get().uri("/api/locations/" + UUID.randomUUID() + "/outreach-drafts").exchange().expectStatus().isNotFound();
        ada.client().post().uri("/api/projects").bodyValue(Map.of("title", "Neon Nights")).exchange().expectStatus().isCreated();
    }
}
