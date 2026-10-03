package com.cinescout.web;

import org.junit.jupiter.api.Test;

/** With no job secret configured the job endpoint is not there at all, whatever the caller sends. */
class JobUnconfiguredApiTest extends ApiTest {

    @Test
    void theJobEndpointIsOffWithoutASecret() {
        web.post().uri("/api/internal/jobs/follow-ups").exchange().expectStatus().isNotFound();
        web.post().uri("/api/internal/jobs/follow-ups").header("X-Job-Secret", "").exchange().expectStatus().isNotFound();
        register("Ada").client().post().uri("/api/internal/jobs/follow-ups").exchange().expectStatus().isNotFound();
    }
}
