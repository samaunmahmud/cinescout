package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Comments on a venue, through the whole application: threads, mentions, who may write, edit and delete, and guests. */
class CommentApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Night Shift", "locationArea", "Brooklyn"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String venue(Account member, String projectId) {
        String scene = json(member.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("title", "Diner", "sourceText", "INT. DINER - NIGHT"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        return json(member.client().post().uri("/api/scenes/" + scene + "/locations").bodyValue(Map.of("name", "Moonlight Diner"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private void add(Account owner, String projectId, Account who, String role) {
        owner.client().post().uri("/api/projects/" + projectId + "/members").bodyValue(Map.of("email", who.email(), "role", role))
                .exchange().expectStatus().isCreated();
    }

    private ResponseSpec post(Account member, String venue, Map<String, Object> body) {
        return member.client().post().uri("/api/locations/" + venue + "/comments").bodyValue(body).exchange();
    }

    private String postId(Account member, String venue, Map<String, Object> body) {
        return json(post(member, venue, body).expectStatus().isCreated()).path("id").asText();
    }

    private JsonNode threads(Account member, String venue) {
        return json(member.client().get().uri("/api/locations/" + venue + "/comments").exchange().expectStatus().isOk());
    }

    @Test
    void membersTalkInThreadsAndMentionEachOther() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        Account outsider = register("Mallory");
        String project = project(ada);
        add(ada, project, grace, "EDITOR");
        String venue = venue(ada, project);

        JsonNode first = json(post(ada, venue, Map.of("body", " @Grace can you check the power? ",
                "mentions", List.of(grace.id(), outsider.id(), grace.id()))).expectStatus().isCreated());
        assertThat(first.path("body").asText()).isEqualTo("@Grace can you check the power?");
        assertThat(first.path("authorName").asText()).isEqualTo("Ada");
        assertThat(first.path("guest").asBoolean()).isFalse();
        assertThat(first.path("mentions")).hasSize(1);
        assertThat(first.path("mentions").get(0).path("displayName").asText()).isEqualTo("Grace");
        String thread = first.path("id").asText();

        String reply = postId(grace, venue, Map.of("body", "Three-phase in the basement", "parentId", thread));
        // Answering a reply joins the same thread.
        JsonNode nested = json(post(ada, venue, Map.of("body", "Great", "parentId", reply)).expectStatus().isCreated());
        assertThat(nested.path("parentId").asText()).isEqualTo(thread);
        postId(grace, venue, Map.of("body", "Second thread"));

        JsonNode page = threads(grace, venue);
        assertThat(page.path("totalItems").asInt()).isEqualTo(2);
        JsonNode top = page.path("items").get(0);
        assertThat(top.path("id").asText()).isEqualTo(thread);
        assertThat(top.path("replies")).hasSize(2);
        assertThat(top.path("replies").get(0).path("authorName").asText()).isEqualTo("Grace");
        assertThat(page.path("items").get(1).path("replies")).isEmpty();

        String otherVenue = venue(ada, project);
        post(ada, otherVenue, Map.of("body", "Wrong thread", "parentId", thread)).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("parentId");
        post(ada, venue, Map.of("body", "  ")).expectStatus().isBadRequest();
    }

    @Test
    void authorsEditAndDeleteTheirOwnAndTheOwnerDeletesAny() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        Account hedy = register("Hedy");
        String project = project(ada);
        add(ada, project, grace, "EDITOR");
        add(ada, project, hedy, "EDITOR");
        String venue = venue(ada, project);
        String graces = postId(grace, venue, Map.of("body", "Too loud?"));
        jdbc.update("UPDATE venue_comments SET created_at = created_at - interval '1 minute' WHERE id = ?::uuid", graces);

        JsonNode edited = json(grace.client().put().uri("/api/comments/" + graces).bodyValue(Map.of("body", "Too loud at night?"))
                .exchange().expectStatus().isOk());
        assertThat(edited.path("body").asText()).isEqualTo("Too loud at night?");
        assertThat(edited.path("edited").asBoolean()).isTrue();

        for (Account notAuthor : new Account[] {ada, hedy}) {
            notAuthor.client().put().uri("/api/comments/" + graces).bodyValue(Map.of("body", "Rewritten")).exchange()
                    .expectStatus().isForbidden()
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        }
        hedy.client().delete().uri("/api/comments/" + graces).exchange().expectStatus().isForbidden();
        ada.client().delete().uri("/api/comments/" + graces).exchange().expectStatus().isNoContent();

        String hedys = postId(hedy, venue, Map.of("body", "Mine"));
        postId(grace, venue, Map.of("body", "Reply", "parentId", hedys));
        hedy.client().delete().uri("/api/comments/" + hedys).exchange().expectStatus().isNoContent();
        assertThat(threads(ada, venue).path("totalItems").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venue_comments", Long.class)).isZero();
    }

    @Test
    void aViewerReadsButDoesNotWriteAndSomeoneNotOnTheCrewSeesNothing() {
        Account ada = register("Ada");
        Account viewer = register("Vera");
        Account outsider = register("Mallory");
        String project = project(ada);
        add(ada, project, viewer, "VIEWER");
        String venue = venue(ada, project);
        String comment = postId(ada, venue, Map.of("body", "Booked for a recce"));

        assertThat(threads(viewer, venue).path("items")).hasSize(1);
        post(viewer, venue, Map.of("body", "Can I?")).expectStatus().isForbidden();

        outsider.client().get().uri("/api/locations/" + venue + "/comments").exchange().expectStatus().isNotFound();
        post(outsider, venue, Map.of("body", "Hi")).expectStatus().isNotFound();
        outsider.client().put().uri("/api/comments/" + comment).bodyValue(Map.of("body", "x")).exchange().expectStatus().isNotFound();
        outsider.client().delete().uri("/api/comments/" + comment).exchange().expectStatus().isNotFound();
        ada.client().delete().uri("/api/comments/" + UUID.randomUUID()).exchange().expectStatus().isNotFound();
    }

    @Test
    void aGuestsCommentFromTheDirectorLinkJoinsTheThreadAndFollowsTheirCall() {
        Account ada = register("Ada");
        String project = project(ada);
        String venue = venue(ada, project);
        ada.client().put().uri("/api/locations/" + venue).bodyValue(Map.of("status", "SHORTLISTED")).exchange().expectStatus().isOk();
        String token = json(ada.client().post().uri("/api/projects/" + project + "/director-link").exchange().expectStatus().isOk())
                .path("token").asText();
        String answer = "/api/public/shortlists/" + token + "/venues/" + venue + "/response";

        web.post().uri(answer).bodyValue(Map.of("guestName", "Wes", "verdict", "MAYBE")).exchange().expectStatus().isOk();
        assertThat(threads(ada, venue).path("totalItems").asInt()).isZero();

        web.post().uri(answer).bodyValue(Map.of("guestName", "Wes", "verdict", "APPROVE", "comment", "Love the booths"))
                .exchange().expectStatus().isOk();
        JsonNode guest = threads(ada, venue).path("items").get(0);
        assertThat(guest.path("guest").asBoolean()).isTrue();
        assertThat(guest.path("authorName").asText()).isEqualTo("Wes");
        assertThat(guest.path("authorId").isNull()).isTrue();
        assertThat(guest.path("body").asText()).isEqualTo("Love the booths");
        String guestComment = guest.path("id").asText();

        postId(ada, venue, Map.of("body", "Agreed", "parentId", guestComment));
        web.post().uri(answer).bodyValue(Map.of("guestName", "WES", "verdict", "APPROVE", "comment", "Love the booths, and the neon"))
                .exchange().expectStatus().isOk();
        JsonNode followed = threads(ada, venue).path("items").get(0);
        assertThat(followed.path("id").asText()).isEqualTo(guestComment);
        assertThat(followed.path("authorName").asText()).isEqualTo("WES");
        assertThat(followed.path("body").asText()).isEqualTo("Love the booths, and the neon");
        assertThat(followed.path("replies")).hasSize(1);

        ada.client().put().uri("/api/comments/" + guestComment).bodyValue(Map.of("body", "Edited")).exchange().expectStatus().isForbidden();
        web.post().uri(answer).bodyValue(Map.of("guestName", "Wes", "verdict", "NO")).exchange().expectStatus().isOk();
        assertThat(threads(ada, venue).path("totalItems").asInt()).isZero();
    }
}
