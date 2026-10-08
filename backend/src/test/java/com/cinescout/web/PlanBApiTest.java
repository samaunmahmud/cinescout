package com.cinescout.web;

import com.cinescout.llm.LlmClient;
import com.cinescout.llm.LlmException;
import com.cinescout.llm.LlmException.Kind;
import com.cinescout.outreach.OutreachEmail;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Weather plan B end to end with only the LLM replaced: the backup venue pencilled for the day, and the email drafted. */
@SpringBootTest(properties = {
        "cinescout.llm.watsonx.api-key=dummy", "cinescout.llm.watsonx.project-id=dummy", "cinescout.llm.watsonx.model-id=dummy",
        "cinescout.resilience.initial-backoff=1ms", "cinescout.resilience.max-backoff=5ms",
        "cinescout.resilience.breaker-window-size=100", "cinescout.resilience.breaker-minimum-calls=100"
})
class PlanBApiTest extends ApiTest {

    private static final OutreachEmail EMAIL = new OutreachEmail("Backup booking: Neon Nights", "Hello,", List.of("Could you hold the hall?"), "Ada");

    @MockitoBean LlmClient llm;

    @BeforeEach
    void defaultAnswers() {
        when(llm.modelId()).thenReturn("ibm/test-model");
        when(llm.generate(any(), any(), eq(OutreachEmail.class))).thenReturn(Mono.just(EMAIL));
    }

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    /** A scene with a confirmed rooftop and a backup hall: [project, scene, rooftop, hall]. */
    private String[] scene(Account owner) {
        String project = json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Neon Nights", "locationArea", "London"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        String scene = json(owner.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("title", "Rooftop chase", "sourceText", "EXT. ROOFTOP - DAY", "shootDateStart", "2026-11-02"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        String rooftop = json(owner.client().post().uri("/api/scenes/" + scene + "/locations").bodyValue(Map.of("name", "Skyline Terrace"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        owner.client().put().uri("/api/locations/" + rooftop).bodyValue(Map.of("status", "CONFIRMED")).exchange().expectStatus().isOk();
        String hall = json(owner.client().post().uri("/api/scenes/" + scene + "/locations").bodyValue(Map.of("name", "Tannery Hall"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        return new String[] {project, scene, rooftop, hall};
    }

    @Test
    void theBackupIsPencilledForTheDayAndItsOwnerAskedToHoldIt() {
        Account ada = register("Ada");
        String[] ids = scene(ada);

        JsonNode planB = json(ada.client().post().uri("/api/locations/" + ids[3] + "/plan-b")
                .bodyValue(Map.of("day", "2026-11-02", "reason", "Rain likely: 80%")).exchange().expectStatus().isOk());

        assertThat(planB.path("availability").get(0).path("day").asText()).isEqualTo("2026-11-02");
        assertThat(planB.path("availability").get(0).path("state").asText()).isEqualTo("PENCILLED");
        assertThat(planB.path("availability").get(0).path("note").asText()).contains("Weather plan B for Rooftop chase", "Rain likely: 80%");
        assertThat(planB.path("draft").path("subject").asText()).isEqualTo("Backup booking: Neon Nights");
        assertThat(planB.path("draftProblem").isNull()).isTrue();
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).generate(any(), prompt.capture(), eq(OutreachEmail.class));
        assertThat(prompt.getValue()).contains("weather backup", "Monday 2 November 2026", "Rain likely: 80%");
    }

    @Test
    void theHoldIsMadeEvenWhenTheEmailCannotBeAndNotForTheConfirmedVenue() {
        when(llm.generate(any(), any(), eq(OutreachEmail.class))).thenReturn(Mono.error(new LlmException(Kind.UNAVAILABLE, "down")));
        Account ada = register("Ada");
        String[] ids = scene(ada);

        JsonNode planB = json(ada.client().post().uri("/api/locations/" + ids[3] + "/plan-b").bodyValue(Map.of("day", "2026-11-02"))
                .exchange().expectStatus().isOk());
        assertThat(planB.path("availability")).hasSize(1);
        assertThat(planB.path("draft").isNull()).isTrue();
        assertThat(planB.path("draftProblem").asText()).contains("could not write the email");

        ada.client().post().uri("/api/locations/" + ids[2] + "/plan-b").bodyValue(Map.of("day", "2026-11-02")).exchange().expectStatus().isEqualTo(409);
        register("Mal").client().post().uri("/api/locations/" + ids[3] + "/plan-b").bodyValue(Map.of("day", "2026-11-02"))
                .exchange().expectStatus().isNotFound();
        ada.client().post().uri("/api/locations/" + ids[3] + "/plan-b").bodyValue(Map.of("reason", "rain")).exchange().expectStatus().isBadRequest();
    }
}
