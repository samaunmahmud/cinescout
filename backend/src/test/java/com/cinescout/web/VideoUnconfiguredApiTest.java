package com.cinescout.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

/** The default deployment: no YouTube key, so videos are not available, and the endpoint says so. */
class VideoUnconfiguredApiTest extends ApiTest {

    @Test
    void videosAre503WithAnExplanationWhenNoKeyIsConfigured() {
        register("Ada").client().get().uri("/api/locations/" + UUID.randomUUID() + "/videos").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.detail").isEqualTo("Videos are not configured on this server");
    }

    @Test
    void videosNeedALogin() {
        web.get().uri("/api/locations/" + UUID.randomUUID() + "/videos").exchange().expectStatus().isUnauthorized();
    }
}
