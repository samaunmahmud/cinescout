package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Reply tracking with Postmark set up: the webhook files replies by their address; members can paste them in too. */
@SpringBootTest(properties = {
        "cinescout.mail.reply-domain=replies.example.com",
        "cinescout.mail.inbound.provider=postmark",
        "cinescout.mail.inbound.username=hook",
        "cinescout.mail.inbound.password=s3cret"
})
class ReplyApiTest extends ApiTest {

    static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef";

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    /** A project with a venue and one sent outreach email whose reply token is {@link #TOKEN}; returns the draft id. */
    static String sentDraft(ApiTest test, Account owner, org.springframework.jdbc.core.JdbcTemplate jdbc, String token) {
        JsonNode project = owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Night Shift", "locationArea", "London"))
                .exchange().expectBody(JsonNode.class).returnResult().getResponseBody();
        JsonNode scene = owner.client().post().uri("/api/projects/" + project.path("id").asText() + "/scenes")
                .bodyValue(Map.of("title", "Pub", "sourceText", "INT. PUB")).exchange().expectBody(JsonNode.class).returnResult().getResponseBody();
        JsonNode venue = owner.client().post().uri("/api/scenes/" + scene.path("id").asText() + "/locations").bodyValue(Map.of("name", "The Lamb"))
                .exchange().expectBody(JsonNode.class).returnResult().getResponseBody();
        UUID draft = UUID.randomUUID();
        jdbc.update("INSERT INTO outreach_drafts (id, location_id, created_by, subject, body, tone, status, sent_at, reply_token) "
                        + "VALUES (?, ?::uuid, ?, 'Filming at The Lamb', 'Dear owner', 'PROFESSIONAL', 'SENT', now(), ?)",
                draft, venue.path("id").asText(), owner.id(), token);
        return draft.toString();
    }

    private ResponseSpec webhook(String auth, String to) {
        String json = """
                {"From":"owner@lamb.example","FromFull":{"Email":"owner@lamb.example","Name":"Sal"},
                 "ToFull":[{"Email":"%s"}],"Subject":"Re: Filming at The Lamb","Date":"Fri, 2 Oct 2026 14:05:00 +0100",
                 "TextBody":"Yes, Tuesdays work.","StrippedTextReply":"Yes, Tuesdays work."}""".formatted(to);
        return web.post().uri("/api/inbound/postmark").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json).exchange();
    }

    private static final String RIGHT = "Basic " + java.util.Base64.getEncoder().encodeToString("hook:s3cret".getBytes());

    @Test
    void aReplyToTheDraftsAddressIsFiledAndMarksItReplied() {
        Account ada = register("Ada");
        String draft = sentDraft(this, ada, jdbc, TOKEN);
        JsonNode before = json(ada.client().get().uri("/api/outreach-drafts/" + draft).exchange().expectStatus().isOk());
        assertThat(before.path("replyTo").asText()).isEqualTo("scout+" + TOKEN + "@replies.example.com");

        webhook(RIGHT, "SCOUT+" + TOKEN.toUpperCase() + "@replies.example.com").expectStatus().isOk();

        JsonNode replies = json(ada.client().get().uri("/api/outreach-drafts/" + draft + "/replies").exchange().expectStatus().isOk());
        assertThat(replies.path("totalItems").asInt()).isEqualTo(1);
        JsonNode reply = replies.path("items").get(0);
        assertThat(reply.path("source").asText()).isEqualTo("INBOUND");
        assertThat(reply.path("fromAddress").asText()).isEqualTo("owner@lamb.example");
        assertThat(reply.path("text").asText()).isEqualTo("Yes, Tuesdays work.");
        assertThat(reply.path("receivedAt").asText()).isEqualTo("2026-10-02T13:05:00Z");
        assertThat(json(ada.client().get().uri("/api/outreach-drafts/" + draft).exchange()).path("status").asText()).isEqualTo("REPLIED");
    }

    @Test
    void theWebhookTrustsOnlyItsCredentialsAndQuietlyDropsMailForNoDraft() {
        Account ada = register("Ada");
        String draft = sentDraft(this, ada, jdbc, TOKEN);

        webhook("Basic " + java.util.Base64.getEncoder().encodeToString("hook:guess".getBytes()), "scout+" + TOKEN + "@replies.example.com")
                .expectStatus().isUnauthorized();
        webhook(RIGHT, "scout+ffffffffffffffffffffffffffffffffffffffffffffffff@replies.example.com").expectStatus().isOk();
        web.post().uri("/api/inbound/sendgrid").header("Authorization", RIGHT).bodyValue("{}").exchange().expectStatus().isNotFound();
        assertThat(json(ada.client().get().uri("/api/outreach-drafts/" + draft + "/replies").exchange()).path("totalItems").asInt()).isZero();
        assertThat(json(ada.client().get().uri("/api/outreach-drafts/" + draft).exchange()).path("status").asText()).isEqualTo("SENT");
    }

    @Test
    void aMemberPastesAReplyInAndOnlyEditorsMay() {
        Account ada = register("Ada");
        Account vera = register("Vera");
        Account outsider = register("Mallory");
        String draft = sentDraft(this, ada, jdbc, TOKEN);
        String project = jdbc.queryForObject("SELECT s.project_id::text FROM outreach_drafts d JOIN locations l ON l.id = d.location_id "
                + "JOIN scenes s ON s.id = l.scene_id WHERE d.id = ?::uuid", String.class, draft);
        ada.client().post().uri("/api/projects/" + project + "/members").bodyValue(Map.of("email", vera.email(), "role", "VIEWER"))
                .exchange().expectStatus().isCreated();

        JsonNode pasted = json(ada.client().post().uri("/api/outreach-drafts/" + draft + "/replies")
                .bodyValue(Map.of("fromName", "Sal", "text", "Call me on Monday")).exchange().expectStatus().isCreated());
        assertThat(pasted.path("source").asText()).isEqualTo("MANUAL");
        assertThat(pasted.path("recordedBy").asText()).isEqualTo("Ada");
        assertThat(json(ada.client().get().uri("/api/outreach-drafts/" + draft).exchange()).path("status").asText()).isEqualTo("REPLIED");
        ada.client().post().uri("/api/outreach-drafts/" + draft + "/replies").bodyValue(Map.of()).exchange().expectStatus().isCreated();
        ada.client().post().uri("/api/outreach-drafts/" + draft + "/replies").bodyValue(Map.of("fromAddress", "not an email"))
                .exchange().expectStatus().isBadRequest();

        vera.client().get().uri("/api/outreach-drafts/" + draft + "/replies").exchange().expectStatus().isOk();
        vera.client().post().uri("/api/outreach-drafts/" + draft + "/replies").bodyValue(Map.of()).exchange().expectStatus().isForbidden();
        outsider.client().get().uri("/api/outreach-drafts/" + draft + "/replies").exchange().expectStatus().isNotFound();
    }
}
