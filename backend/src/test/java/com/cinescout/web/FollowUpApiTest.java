package com.cinescout.web;

import com.cinescout.llm.LlmClient;
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

/**
 * Follow-up nudges end to end: the project setting, the job that flags unanswered emails (run through its endpoint, as
 * the scheduled workflow does), the badge counts and the drafted chaser, with only the LLM mocked.
 */
@SpringBootTest(properties = {
        "cinescout.llm.watsonx.api-key=dummy", "cinescout.llm.watsonx.project-id=dummy", "cinescout.llm.watsonx.model-id=dummy",
        "cinescout.jobs.secret=" + FollowUpApiTest.SECRET
})
class FollowUpApiTest extends ApiTest {

    static final String SECRET = "test-job-secret";
    private static final String SCRIPT = "INT. ROOFTOP BAR - NIGHT. SECRET-PLOT: the detective is the killer.";
    private static final OutreachEmail CHASER = new OutreachEmail("Following up", "Hello Tom,",
            List.of("I am following up on my email about filming at The Sky Bar."), "Best wishes,\nAda");

    @MockitoBean LlmClient llm;

    @BeforeEach
    void defaultAnswers() {
        when(llm.modelId()).thenReturn("ibm/test-model");
        when(llm.generate(any(), any(), eq(OutreachEmail.class))).thenReturn(Mono.just(CHASER));
    }

    // --- helpers ----------------------------------------------------------------------------------

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Neon Nights"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String location(Account owner, String projectId) {
        String scene = json(owner.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("sceneNumber", 1, "title", "Rooftop", "sourceText", SCRIPT))
                .exchange().expectStatus().isCreated()).path("id").asText();
        return json(owner.client().post().uri("/api/scenes/" + scene + "/locations")
                .bodyValue(Map.of("name", "The Sky Bar", "notes", "PRIVATE-NOTE call after 5pm"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    /** An email to the venue, marked as sent {@code daysAgo} days ago. */
    private String sentEmail(String locationId, int daysAgo) {
        String id = jdbc.queryForObject("""
                INSERT INTO outreach_drafts (location_id, created_by, recipient_name, recipient_email, subject, body, status, sent_at, reply_token)
                SELECT ?::uuid, p.owner_id, 'Tom', 'tom@skybar.example', 'Location enquiry: Neon Nights', 'FIRST-EMAIL-BODY', 'SENT',
                       now() - make_interval(days => ?), md5(random()::text)
                FROM locations l JOIN scenes s ON s.id = l.scene_id JOIN projects p ON p.id = s.project_id WHERE l.id = ?::uuid
                RETURNING id""", String.class, locationId, daysAgo, locationId);
        return id;
    }

    private ResponseSpec runJob(String secret) {
        return web.post().uri("/api/internal/jobs/follow-ups").header("X-Job-Secret", secret).exchange();
    }

    private JsonNode draft(Account who, String draftId) {
        return json(who.client().get().uri("/api/outreach-drafts/" + draftId).exchange().expectStatus().isOk());
    }

    private void addMember(Account owner, String projectId, Account who, String role) {
        owner.client().post().uri("/api/projects/" + projectId + "/members").bodyValue(Map.of("email", who.email(), "role", role))
                .exchange().expectStatus().is2xxSuccessful();
    }

    // --- the setting ------------------------------------------------------------------------------

    @Test
    void theFollowUpPeriodIsFiveDaysUntilAnEditorChangesItAndViewersOnlyReadIt() {
        Account ada = register("Ada");
        Account vera = register("Vera");
        Account mallory = register("Mallory");
        String project = project(ada);
        addMember(ada, project, vera, "VIEWER");

        assertThat(json(vera.client().get().uri("/api/projects/" + project + "/settings").exchange().expectStatus().isOk())
                .path("followUpDays").asInt()).isEqualTo(5);
        assertThat(json(ada.client().put().uri("/api/projects/" + project + "/settings").bodyValue(Map.of("followUpDays", 3))
                .exchange().expectStatus().isOk()).path("followUpDays").asInt()).isEqualTo(3);

        vera.client().put().uri("/api/projects/" + project + "/settings").bodyValue(Map.of("followUpDays", 9))
                .exchange().expectStatus().isForbidden();
        mallory.client().get().uri("/api/projects/" + project + "/settings").exchange().expectStatus().isNotFound();
        mallory.client().put().uri("/api/projects/" + project + "/settings").bodyValue(Map.of("followUpDays", 9))
                .exchange().expectStatus().isNotFound();
        ada.client().put().uri("/api/projects/" + project + "/settings").bodyValue(Map.of("followUpDays", 0))
                .exchange().expectStatus().isBadRequest();
        assertThat(jdbc.queryForObject("SELECT follow_up_days FROM projects WHERE id = ?::uuid", Integer.class, project)).isEqualTo(3);
    }

    // --- the job ----------------------------------------------------------------------------------

    @Test
    void theJobNeedsTheSharedSecretAndNoUserLogin() {
        runJob("wrong").expectStatus().isUnauthorized();
        web.post().uri("/api/internal/jobs/follow-ups").exchange().expectStatus().isUnauthorized();
        runJob(SECRET).expectStatus().isOk();
        web.post().uri("/api/internal/jobs/no-such-job").header("X-Job-Secret", SECRET).exchange().expectStatus().isNotFound();
        // A user's login is not the secret.
        register("Ada").client().post().uri("/api/internal/jobs/follow-ups").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void theJobFlagsOnlySentEmailsLeftUnansweredLongerThanTheProjectAllows() {
        Account ada = register("Ada");
        String project = project(ada);
        String venue = location(ada, project);
        String overdue = sentEmail(venue, 6);
        String recent = sentEmail(venue, 2);
        String answered = sentEmail(venue, 9);
        jdbc.update("INSERT INTO outreach_replies (draft_id, source, received_at) VALUES (?::uuid, 'MANUAL', now())", answered);
        String archivedProject = project(ada);
        String archived = sentEmail(location(ada, archivedProject), 9);
        jdbc.update("UPDATE projects SET status = 'ARCHIVED' WHERE id = ?::uuid", archivedProject);

        JsonNode run = json(runJob(SECRET).expectStatus().isOk());
        assertThat(run.path("job").asText()).isEqualTo("follow-ups");
        assertThat(run.path("affected").asInt()).isEqualTo(1);
        assertThat(draft(ada, overdue).path("followUpFlaggedAt").asText()).isNotBlank();
        for (String id : new String[] {recent, answered, archived}) {
            assertThat(draft(ada, id).path("followUpFlaggedAt").isNull()).as(id).isTrue();
        }
        // Running it again changes nothing.
        assertThat(json(runJob(SECRET).expectStatus().isOk()).path("affected").asInt()).isZero();

        // A shorter period catches the recent one on the next run.
        ada.client().put().uri("/api/projects/" + project + "/settings").bodyValue(Map.of("followUpDays", 1)).exchange().expectStatus().isOk();
        assertThat(json(runJob(SECRET).expectStatus().isOk()).path("affected").asInt()).isEqualTo(1);

        JsonNode card = json(ada.client().get().uri("/api/projects/" + project).exchange().expectStatus().isOk());
        assertThat(card.path("followUpCount").asLong()).isEqualTo(2);
        JsonNode due = json(ada.client().get().uri("/api/projects/" + project + "/outreach-drafts?followUp=true").exchange().expectStatus().isOk());
        assertThat(due.path("items").findValuesAsText("id")).containsExactly(overdue, recent);
        assertThat(due.path("items").get(0).path("followUpFlaggedAt").asText()).isNotBlank();
    }

    @Test
    void anEmailMarkedRepliedNoLongerWaitsOnAFollowUp() {
        Account ada = register("Ada");
        String project = project(ada);
        String email = sentEmail(location(ada, project), 7);
        runJob(SECRET).expectStatus().isOk();

        ada.client().post().uri("/api/outreach-drafts/" + email + "/replies").bodyValue(Map.of("text", "Yes, call us"))
                .exchange().expectStatus().is2xxSuccessful();

        assertThat(draft(ada, email).path("followUpFlaggedAt").isNull()).isTrue();
        assertThat(json(ada.client().get().uri("/api/projects/" + project).exchange().expectStatus().isOk()).path("followUpCount").asLong()).isZero();
    }

    // --- the chaser -------------------------------------------------------------------------------

    @Test
    void aFollowUpIsANewDraftThatChasesTheFirstEmailAndTellsTheModelNothingPrivate() {
        Account ada = register("Ada");
        String project = project(ada);
        String email = sentEmail(location(ada, project), 6);
        runJob(SECRET).expectStatus().isOk();

        JsonNode chaser = json(ada.client().post().uri("/api/outreach-drafts/" + email + "/follow-up").exchange().expectStatus().isCreated());

        assertThat(chaser.path("subject").asText()).isEqualTo("Re: Location enquiry: Neon Nights");
        assertThat(chaser.path("body").asText()).startsWith("Hello Tom,").endsWith("Ada");
        assertThat(chaser.path("status").asText()).isEqualTo("DRAFT");
        assertThat(chaser.path("followUpOfId").asText()).isEqualTo(email);
        assertThat(chaser.path("recipientEmail").asText()).isEqualTo("tom@skybar.example");
        assertThat(draft(ada, email).path("followUpFlaggedAt").isNull()).isTrue();

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).generate(any(), prompt.capture(), eq(OutreachEmail.class));
        assertThat(prompt.getValue()).contains("Location enquiry: Neon Nights", "The Sky Bar", "Recipient: Tom")
                .doesNotContain("SECRET-PLOT", "PRIVATE-NOTE", "FIRST-EMAIL-BODY", "fit");

        // The first email has a follow-up now, so the job leaves it alone.
        assertThat(json(runJob(SECRET).expectStatus().isOk()).path("affected").asInt()).isZero();
        // Deleting the chaser lets the first email be flagged again.
        ada.client().delete().uri("/api/outreach-drafts/" + chaser.path("id").asText()).exchange().expectStatus().isNoContent();
        assertThat(json(runJob(SECRET).expectStatus().isOk()).path("affected").asInt()).isEqualTo(1);
    }

    @Test
    void onlyASentEmailCanBeFollowedUpAndOnlyByAnEditor() {
        Account ada = register("Ada");
        Account vera = register("Vera");
        Account mallory = register("Mallory");
        String project = project(ada);
        addMember(ada, project, vera, "VIEWER");
        String venue = location(ada, project);
        String email = sentEmail(venue, 6);
        jdbc.update("UPDATE outreach_drafts SET status = 'DRAFT', sent_at = NULL WHERE id = ?::uuid", email);

        JsonNode problem = json(ada.client().post().uri("/api/outreach-drafts/" + email + "/follow-up").exchange().expectStatus().isEqualTo(409));
        assertThat(problem.path("detail").asText()).contains("sent");
        jdbc.update("UPDATE outreach_drafts SET status = 'REPLIED', sent_at = now() WHERE id = ?::uuid", email);
        ada.client().post().uri("/api/outreach-drafts/" + email + "/follow-up").exchange().expectStatus().isEqualTo(409);

        jdbc.update("UPDATE outreach_drafts SET status = 'SENT' WHERE id = ?::uuid", email);
        vera.client().post().uri("/api/outreach-drafts/" + email + "/follow-up").exchange().expectStatus().isForbidden();
        mallory.client().post().uri("/api/outreach-drafts/" + email + "/follow-up").exchange().expectStatus().isNotFound();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outreach_drafts WHERE follow_up_of IS NOT NULL", Integer.class)).isZero();
    }
}
