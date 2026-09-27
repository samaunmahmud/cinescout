package com.cinescout.web;

import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.llm.LlmClient;
import com.cinescout.search.LocationSearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The limits on the real filter chain. Each test comes from its own client address (X-Forwarded-For, trusted here
 * as it is behind nginx) so the per-address limits of one test do not reach the next.
 */
@SpringBootTest(properties = {
        "cinescout.rate-limits.enabled=true",
        "cinescout.rate-limits.ai.capacity=2", "cinescout.rate-limits.ai.period=1h",
        "cinescout.rate-limits.login.capacity=3", "cinescout.rate-limits.login.period=10m",
        "cinescout.rate-limits.register.capacity=3", "cinescout.rate-limits.register.period=1h",
        "server.forward-headers-strategy=framework",
        "cinescout.llm.watsonx.api-key=dummy", "cinescout.llm.watsonx.project-id=dummy", "cinescout.llm.watsonx.model-id=dummy",
        "cinescout.search.parallel.api-key=dummy"
})
class RateLimitApiTest extends ApiTest {

    private static final String XFF = "X-Forwarded-For";

    @MockitoBean LlmClient llm;
    @MockitoBean LocationSearchClient search;

    private Account registerFrom(String address, String name) {
        String email = uniqueEmail(name);
        web.post().uri("/api/auth/register").header(XFF, address)
                .bodyValue(Map.of("email", email, "password", PASSWORD, "displayName", name))
                .exchange().expectStatus().isCreated();
        return new Account(null, email, as(email, PASSWORD));
    }

    private WebTestClient.ResponseSpec login(String address, String email, String password) {
        return web.post().uri("/api/auth/login").header(XFF, address).header("X-Requested-With", "XMLHttpRequest")
                .bodyValue(Map.of("email", email, "password", password)).exchange();
    }

    private String scene(Account owner) {
        JsonNode project = owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Neon", "locationArea", "Brooklyn"))
                .exchange().expectStatus().isCreated().expectBody(JsonNode.class).returnResult().getResponseBody();
        return owner.client().post().uri("/api/projects/" + project.path("id").asText() + "/scenes")
                .bodyValue(Map.of("title", "Rooftop", "sourceText", "INT. ROOFTOP BAR - NIGHT."))
                .exchange().expectStatus().isCreated().expectBody(JsonNode.class).returnResult().getResponseBody()
                .path("id").asText();
    }

    @Test
    void aiCallsAreLimitedPerUserWithA429ThatSaysWhenToComeBack() {
        when(llm.generate(any(), any(), eq(SceneRequirements.class)))
                .thenReturn(Mono.just(new SceneRequirements("rooftop bar", "neon", null, "night", AcousticSensitivity.LOW, 5)));
        Account ada = registerFrom("203.0.113.1", "Ada");
        Account grace = registerFrom("203.0.113.1", "Grace");
        String scene = scene(ada);

        ada.client().post().uri("/api/scenes/" + scene + "/parse").exchange().expectStatus().isOk();
        ada.client().post().uri("/api/scenes/" + scene + "/parse").exchange().expectStatus().isOk();
        ada.client().post().uri("/api/scenes/" + scene + "/parse").exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                // Two calls an hour: the next one in (just under, as the test takes time) half an hour.
                .expectHeader().value(HttpHeaders.RETRY_AFTER, seconds -> assertThat(Integer.parseInt(seconds)).isBetween(1700, 1800))
                .expectBody()
                .jsonPath("$.title").isEqualTo("Too many requests")
                .jsonPath("$.detail").isEqualTo("You have reached the limit on AI requests for now; try again in 30 minutes.");

        // Another user's allowance is untouched; outreach generation draws on the same AI allowance as parsing.
        String graceScene = scene(grace);
        grace.client().post().uri("/api/scenes/" + graceScene + "/parse").exchange().expectStatus().isOk();
        ada.client().post().uri("/api/locations/00000000-0000-0000-0000-000000000000/outreach-drafts/generate").exchange()
                .expectStatus().isEqualTo(429);
    }

    @Test
    void failedLoginsAreLimitedPerAddressForTheLoginFormAndBasicAlike() {
        Account ada = registerFrom("203.0.113.2", "Ada");

        login("198.51.100.7", ada.email(), "wrong-1").expectStatus().isUnauthorized();
        login("198.51.100.7", ada.email(), "wrong-2").expectStatus().isUnauthorized();
        web.get().uri("/api/auth/me").header(XFF, "198.51.100.7")
                .headers(h -> h.setBasicAuth(ada.email(), "wrong-3")).exchange().expectStatus().isUnauthorized();

        // Out of attempts: even the right password is refused, by the form and by Basic, until the wait is over.
        login("198.51.100.7", ada.email(), PASSWORD)
                .expectStatus().isEqualTo(429)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER)
                .expectBody().jsonPath("$.detail").value(detail -> assertThat((String) detail).startsWith("Too many failed logins"));
        web.get().uri("/api/auth/me").header(XFF, "198.51.100.7")
                .headers(h -> h.setBasicAuth(ada.email(), PASSWORD)).exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE);

        // Another address is not affected.
        login("198.51.100.8", ada.email(), PASSWORD).expectStatus().isOk();
    }

    @Test
    void successfulLoginsDoNotCount() {
        Account ada = registerFrom("203.0.113.3", "Ada");

        for (int i = 0; i < 6; i++) {
            login("198.51.100.9", ada.email(), PASSWORD).expectStatus().isOk();
            web.get().uri("/api/auth/me").header(XFF, "198.51.100.9")
                    .headers(h -> h.setBasicAuth(ada.email(), PASSWORD)).exchange().expectStatus().isOk();
        }
    }

    @Test
    void registrationIsLimitedPerAddress() {
        for (int i = 0; i < 3; i++) {
            registerFrom("192.0.2.10", "User" + i);
        }

        web.post().uri("/api/auth/register").header(XFF, "192.0.2.10")
                .bodyValue(Map.of("email", uniqueEmail("late"), "password", PASSWORD, "displayName", "Late"))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectBody().jsonPath("$.detail").value(detail -> assertThat((String) detail).startsWith("Too many accounts"));
        registerFrom("192.0.2.11", "Elsewhere");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(4);
    }
}
