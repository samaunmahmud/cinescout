package com.cinescout.web;

import com.cinescout.domain.OutreachTone;
import com.cinescout.llm.LlmClient;
import com.cinescout.llm.LlmException;
import com.cinescout.llm.LlmException.Kind;
import com.cinescout.outreach.OutreachEmail;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The outreach endpoints end to end (HTTP, security, the real generator and services, PostgreSQL) with
 * only the LLM replaced by a mock. Only the watsonx.ai key is set: outreach must not need the search key.
 */
@SpringBootTest(properties = {
        "cinescout.llm.watsonx.api-key=dummy", "cinescout.llm.watsonx.project-id=dummy", "cinescout.llm.watsonx.model-id=dummy",
        // Fast retries, and a breaker that cannot open mid-suite and disturb later tests.
        "cinescout.resilience.initial-backoff=1ms", "cinescout.resilience.max-backoff=5ms",
        "cinescout.resilience.breaker-window-size=100", "cinescout.resilience.breaker-minimum-calls=100"
})
class OutreachApiTest extends ApiTest {

    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;
    private static final String SCRIPT = "INT. ROOFTOP BAR - NIGHT. SECRET-PLOT: the detective is the killer.";
    private static final OutreachEmail EMAIL = new OutreachEmail("Location enquiry: Neon Nights", "Hello,\n\nMay we film at your bar?\n\nAda");

    @MockitoBean LlmClient llm;

    @BeforeEach
    void defaultAnswers() {
        when(llm.modelId()).thenReturn("ibm/test-model");
        when(llm.generate(any(), any(), eq(OutreachEmail.class))).thenReturn(Mono.just(EMAIL));
    }

    // --- helpers ----------------------------------------------------------------------------------

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects")
                .bodyValue(Map.of("title", "Neon Nights", "description", "CONFIDENTIAL-DESCRIPTION", "locationArea", "Brooklyn, New York"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String scene(Account owner, String projectId) {
        return json(owner.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("sceneNumber", 1, "title", "Rooftop", "sourceText", SCRIPT,
                        "shootDateStart", "2026-10-01", "shootDateEnd", "2026-10-03"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String location(Account owner, String sceneId) {
        return json(owner.client().post().uri("/api/scenes/" + sceneId + "/locations")
                .bodyValue(Map.of("name", "The Sky Bar", "address", "1 Roof St, Brooklyn", "notes", "PRIVATE-NOTE call after 5pm"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String locationOf(Account owner) {
        return location(owner, scene(owner, project(owner)));
    }

    /** What scouting would have stored for the venue, without needing the search provider. */
    private void givenScoutedDetails(String locationId, String sceneId) {
        jdbc.update("UPDATE locations SET source_excerpt = ?, booking_friction = 'COMMERCIAL', friction_note = ? WHERE id = ?::uuid",
                "A rooftop bar with skyline views", "Enquire via the events team", locationId);
        jdbc.update("""
                UPDATE scenes SET parse_status = 'PARSED', setting_type = 'rooftop bar', visual_mood = 'neon noir',
                       time_of_day = 'night', acoustic_sensitivity = 'HIGH', estimated_crew_size = 12 WHERE id = ?::uuid""", sceneId);
    }

    private JsonNode generate(Account owner, String locationId, Object body) {
        return json(owner.client().post().uri("/api/locations/" + locationId + "/outreach-drafts/generate")
                .bodyValue(body).exchange().expectStatus().isCreated());
    }

    private String userPrompt() {
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm, atLeastOnce()).generate(any(), prompt.capture(), eq(OutreachEmail.class));
        return prompt.getValue();
    }

    private int draftCount(String locationId) {
        return jdbc.queryForObject("SELECT count(*) FROM outreach_drafts WHERE location_id = ?::uuid", Integer.class, locationId);
    }

    // --- generate -----------------------------------------------------------------------------------

    @Test
    void generatingSavesTheModelsEmailAsADraftAndPointsAtIt() {
        Account ada = register("Ada");
        String location = locationOf(ada);

        ResponseSpec response = ada.client().post().uri("/api/locations/" + location + "/outreach-drafts/generate")
                .bodyValue(Map.of("tone", "FRIENDLY", "recipientName", "Sam", "recipientEmail", "sam@skybar.example.com"))
                .exchange().expectStatus().isCreated();
        JsonNode draft = json(response);

        assertThat(draft.path("locationId").asText()).isEqualTo(location);
        assertThat(draft.path("subject").asText()).isEqualTo("Location enquiry: Neon Nights");
        assertThat(draft.path("body").asText()).isEqualTo(EMAIL.body());
        assertThat(draft.path("tone").asText()).isEqualTo("FRIENDLY");
        assertThat(draft.path("recipientName").asText()).isEqualTo("Sam");
        assertThat(draft.path("recipientEmail").asText()).isEqualTo("sam@skybar.example.com");
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.path("generatedBy").asText()).isEqualTo("ibm/test-model");
        assertThat(draft.path("sentAt").isNull()).isTrue();
        assertThat(Instant.parse(draft.path("createdAt").asText())).isNotNull();
        String id = draft.path("id").asText();
        assertThat(jdbc.queryForObject("SELECT created_by FROM outreach_drafts WHERE id = ?::uuid", UUID.class, id)).isEqualTo(ada.id());
        ada.client().get().uri("/api/outreach-drafts/" + id).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.body").isEqualTo(EMAIL.body());
    }

    @Test
    void theBodyIsOptionalAndTheToneDefaultsToProfessional() {
        Account ada = register("Ada");

        ada.client().post().uri("/api/locations/" + locationOf(ada) + "/outreach-drafts/generate").exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/api/outreach-drafts/[0-9a-f-]{36}")
                .expectBody()
                .jsonPath("$.tone").isEqualTo(OutreachTone.PROFESSIONAL.name())
                .jsonPath("$.recipientName").isEmpty();
        assertThat(userPrompt()).contains("Tone: PROFESSIONAL", "Recipient: not known");
    }

    @Test
    void theModelSeesTheFactsItNeedsButNeverTheScriptOrTheUsersPrivateNotes() {
        Account ada = register("Ada");
        String project = project(ada);
        String scene = scene(ada, project);
        String location = location(ada, scene);
        givenScoutedDetails(location, scene);

        generate(ada, location, Map.of("tone", "CONCISE", "recipientName", "Sam", "additionalContext", "We can shoot on a weekday"));

        assertThat(userPrompt())
                .contains("Sender: Ada", "Production: Neon Nights", "Recipient: Sam", "Tone: CONCISE",
                        "Booking route: COMMERCIAL", "Booking note: Enquire via the events team",
                        "Shoot dates: 2026-10-01 to 2026-10-03",
                        "- Setting: rooftop bar", "- Time of day: night", "- People on set: 12",
                        "Venue: The Sky Bar", "Address: 1 Roof St, Brooklyn",
                        "<venue_notes>\nA rooftop bar with skyline views\n</venue_notes>",
                        "<sender_notes>\nWe can shoot on a weekday\n</sender_notes>")
                .doesNotContain("SECRET-PLOT", "CONFIDENTIAL-DESCRIPTION", "PRIVATE-NOTE", "ada-", "@example.com");
    }

    @Test
    void aSceneThatWasNeverParsedAndAVenueAddedByHandStillGetAnEmail() {
        Account ada = register("Ada");

        generate(ada, locationOf(ada), Map.of());

        assertThat(userPrompt()).contains("not analysed yet", "(no venue notes available)", "Booking route: unknown");
    }

    @Test
    void theSystemPromptAndResponseTypeAreThoseOfOutreach() {
        Account ada = register("Ada");

        generate(ada, locationOf(ada), Map.of());

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(llm).generate(system.capture(), any(), eq(OutreachEmail.class));
        assertThat(system.getValue()).contains("location scout").contains("Respond with JSON only");
    }

    @Test
    void theSubjectIsTidiedToOneLineAndTheBodyTrimmed() {
        when(llm.generate(any(), any(), eq(OutreachEmail.class)))
                .thenReturn(Mono.just(new OutreachEmail("  Location\nenquiry \n  for Neon Nights ", "\n\n  Hello,\n\nMay we film?  \n\n")));
        Account ada = register("Ada");

        JsonNode draft = generate(ada, locationOf(ada), Map.of());

        assertThat(draft.path("subject").asText()).isEqualTo("Location enquiry for Neon Nights");
        assertThat(draft.path("body").asText()).isEqualTo("Hello,\n\nMay we film?");
    }

    @Test
    void everyCallWritesANewDraftAndTheListIsNewestFirst() {
        Account ada = register("Ada");
        String location = locationOf(ada);
        when(llm.generate(any(), any(), eq(OutreachEmail.class)))
                .thenReturn(Mono.just(new OutreachEmail("First", "one")), Mono.just(new OutreachEmail("Second", "two")));

        generate(ada, location, Map.of("tone", "FRIENDLY"));
        generate(ada, location, Map.of("tone", "CONCISE"));

        JsonNode drafts = json(ada.client().get().uri("/api/locations/" + location + "/outreach-drafts").exchange().expectStatus().isOk());
        assertThat(drafts.findValuesAsText("subject")).containsExactly("Second", "First");
    }

    @Test
    void anUnusableAnswerIs502AndNothingIsSaved() {
        when(llm.generate(any(), any(), eq(OutreachEmail.class)))
                .thenReturn(Mono.error(new LlmException(Kind.INVALID_OUTPUT, "SECRET model output")));
        Account ada = register("Ada");
        String location = locationOf(ada);

        ada.client().post().uri("/api/locations/" + location + "/outreach-drafts/generate").exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentType(PROBLEM)
                .expectBody()
                .jsonPath("$.retryable").isEqualTo(true)
                .jsonPath("$.detail").value(d -> assertThat(d.toString()).doesNotContain("SECRET"));
        assertThat(draftCount(location)).isZero();
    }

    @Test
    void anLlmOutageIs503TellingTheClientToRetryWithoutLeakingDetails() {
        when(llm.generate(any(), any(), eq(OutreachEmail.class)))
                .thenReturn(Mono.error(new LlmException(Kind.UNAVAILABLE, "SECRET upstream detail")));
        Account ada = register("Ada");
        String location = locationOf(ada);

        ada.client().post().uri("/api/locations/" + location + "/outreach-drafts/generate").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "30")
                .expectHeader().contentType(PROBLEM)
                .expectBody()
                .jsonPath("$.retryable").isEqualTo(true)
                .jsonPath("$.detail").value(d -> assertThat(d.toString()).doesNotContain("SECRET"));
        assertThat(draftCount(location)).isZero();
    }

    @Test
    void aLocationDeletedWhileTheModelIsWritingIs404AndNothingIsSaved() {
        Account ada = register("Ada");
        String location = locationOf(ada);
        when(llm.generate(any(), any(), eq(OutreachEmail.class))).thenAnswer(call -> {
            jdbc.update("DELETE FROM locations WHERE id = ?::uuid", location);
            return Mono.just(EMAIL);
        });

        ada.client().post().uri("/api/locations/" + location + "/outreach-drafts/generate").exchange()
                .expectStatus().isNotFound().expectHeader().contentType(PROBLEM);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outreach_drafts", Integer.class)).isZero();
    }

    @Test
    void badRequestsAre400AndCostNothing() {
        Account ada = register("Ada");
        String location = locationOf(ada);
        String uri = "/api/locations/" + location + "/outreach-drafts/generate";

        json(ada.client().post().uri(uri).bodyValue(Map.of("recipientEmail", "not-an-email")).exchange()
                .expectStatus().isBadRequest().expectHeader().contentType(PROBLEM))
                .path("errors").findValuesAsText("field").forEach(f -> assertThat(f).isEqualTo("recipientEmail"));
        ada.client().post().uri(uri).bodyValue(Map.of("additionalContext", "x".repeat(2001))).exchange().expectStatus().isBadRequest();
        ada.client().post().uri(uri).bodyValue(Map.of("tone", "SHOUTY")).exchange().expectStatus().isBadRequest();
        verifyNoInteractions(llm);
    }

    // --- edit, delete -------------------------------------------------------------------------------

    private JsonNode update(Account owner, String draftId, Map<String, Object> body, int status) {
        return json(owner.client().put().uri("/api/outreach-drafts/" + draftId).bodyValue(body).exchange().expectStatus().isEqualTo(status));
    }

    private static Map<String, Object> edit(String status) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("subject", "  Edited subject ");
        body.put("body", " Edited body \n");
        body.put("tone", "CONCISE");
        body.put("status", status);
        body.put("recipientName", "Sam");
        body.put("recipientEmail", "sam@skybar.example.com");
        return body;
    }

    @Test
    void editingReplacesTheFieldsAndClearsWhatIsNull() {
        Account ada = register("Ada");
        String id = generate(ada, locationOf(ada), Map.of("recipientName", "Old", "recipientEmail", "old@example.com")).path("id").asText();
        Map<String, Object> body = edit("DRAFT");
        body.put("recipientName", "  ");
        body.put("recipientEmail", null);

        JsonNode draft = update(ada, id, body, 200);

        assertThat(draft.path("subject").asText()).isEqualTo("Edited subject");
        assertThat(draft.path("body").asText()).isEqualTo("Edited body");
        assertThat(draft.path("tone").asText()).isEqualTo("CONCISE");
        assertThat(draft.path("recipientName").isNull()).isTrue();
        assertThat(draft.path("recipientEmail").isNull()).isTrue();
        assertThat(draft.path("generatedBy").asText()).isEqualTo("ibm/test-model");
    }

    @Test
    void markingASentDraftStampsWhenAndGoingBackClearsIt() {
        Account ada = register("Ada");
        String id = generate(ada, locationOf(ada), Map.of()).path("id").asText();

        JsonNode sent = update(ada, id, edit("SENT"), 200);
        String stamped = sent.path("sentAt").asText();
        JsonNode replied = update(ada, id, edit("REPLIED"), 200);
        JsonNode back = update(ada, id, edit("DRAFT"), 200);

        assertThat(sent.path("status").asText()).isEqualTo("SENT");
        assertThat(Instant.parse(stamped)).isBeforeOrEqualTo(Instant.now());
        assertThat(replied.path("status").asText()).isEqualTo("REPLIED");
        assertThat(replied.path("sentAt").asText()).isEqualTo(stamped);
        assertThat(back.path("sentAt").isNull()).isTrue();
    }

    @Test
    void anInvalidEditIs400AndChangesNothing() {
        Account ada = register("Ada");
        String id = generate(ada, locationOf(ada), Map.of()).path("id").asText();
        List<String> fields = new ArrayList<>();
        Map<String, Object> bad = edit("SENT");
        bad.put("subject", " ");
        bad.put("body", "");
        bad.put("recipientEmail", "nope");
        bad.put("tone", null);

        update(ada, id, bad, 400).path("errors").forEach(e -> fields.add(e.path("field").asText()));

        assertThat(fields).contains("subject", "body", "recipientEmail", "tone");
        ada.client().get().uri("/api/outreach-drafts/" + id).exchange()
                .expectBody().jsonPath("$.subject").isEqualTo(EMAIL.subject()).jsonPath("$.status").isEqualTo("DRAFT");
    }

    @Test
    void deletingADraftRemovesItAndDeletingTheLocationRemovesItsDrafts() {
        Account ada = register("Ada");
        String location = locationOf(ada);
        String first = generate(ada, location, Map.of()).path("id").asText();
        generate(ada, location, Map.of());

        ada.client().delete().uri("/api/outreach-drafts/" + first).exchange().expectStatus().isNoContent();
        ada.client().get().uri("/api/outreach-drafts/" + first).exchange().expectStatus().isNotFound();
        assertThat(draftCount(location)).isEqualTo(1);

        ada.client().delete().uri("/api/locations/" + location).exchange().expectStatus().isNoContent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outreach_drafts", Integer.class)).isZero();
    }

    // --- access -----------------------------------------------------------------------------------

    @Test
    void anotherUsersLocationAndDraftsAre404ForEveryOperationAndCostNothing() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String graceLocation = locationOf(grace);
        String graceDraft = generate(grace, graceLocation, Map.of()).path("id").asText();
        org.mockito.Mockito.clearInvocations(llm);

        ada.client().post().uri("/api/locations/" + graceLocation + "/outreach-drafts/generate").exchange()
                .expectStatus().isNotFound().expectHeader().contentType(PROBLEM);
        ada.client().get().uri("/api/locations/" + graceLocation + "/outreach-drafts").exchange().expectStatus().isNotFound();
        ada.client().get().uri("/api/outreach-drafts/" + graceDraft).exchange().expectStatus().isNotFound();
        update(ada, graceDraft, edit("SENT"), 404);
        ada.client().delete().uri("/api/outreach-drafts/" + graceDraft).exchange().expectStatus().isNotFound();

        verifyNoInteractions(llm);
        grace.client().get().uri("/api/outreach-drafts/" + graceDraft).exchange()
                .expectBody().jsonPath("$.subject").isEqualTo(EMAIL.subject()).jsonPath("$.status").isEqualTo("DRAFT");
    }

    @Test
    void everyOutreachRouteNeedsALogin() {
        String id = UUID.randomUUID().toString();

        web.post().uri("/api/locations/" + id + "/outreach-drafts/generate").exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/locations/" + id + "/outreach-drafts").exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/outreach-drafts/" + id).exchange().expectStatus().isUnauthorized();
        web.put().uri("/api/outreach-drafts/" + id).bodyValue(edit("SENT")).exchange().expectStatus().isUnauthorized();
        web.delete().uri("/api/outreach-drafts/" + id).exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(llm);
    }
}
