package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A project's crew and what each role may do, over real HTTP, security and PostgreSQL. */
class CrewApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects")
                .bodyValue(Map.of("title", "The Night Ferry", "locationArea", "Brooklyn, New York"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String scene(Account member, String projectId) {
        return json(member.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("title", "Diner", "sourceText", "INT. DINER - NIGHT"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String location(Account member, String sceneId) {
        return json(member.client().post().uri("/api/scenes/" + sceneId + "/locations").bodyValue(Map.of("name", "Moonlight Diner"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private void add(Account owner, String projectId, Account who, String role) {
        owner.client().post().uri("/api/projects/" + projectId + "/members").bodyValue(Map.of("email", who.email(), "role", role))
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.member.role").isEqualTo(role).jsonPath("$.invite").doesNotExist();
    }

    private static Map<String, Object> projectBody(String title, String status) {
        return Map.of("title", title, "locationArea", "Brooklyn, New York", "status", status);
    }

    private void expectForbidden(ResponseSpec response) {
        response.expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.detail").isNotEmpty();
    }

    // --- who sees what ------------------------------------------------------------------------------

    @Test
    void theCreatorOwnsTheProjectAndSomeoneNotOnTheCrewSeesNothingOfIt() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String project = project(ada);
        String scene = scene(ada, project);
        String location = location(ada, scene);

        ada.client().get().uri("/api/projects/" + project).exchange().expectStatus().isOk().expectBody().jsonPath("$.role").isEqualTo("OWNER");
        for (String uri : new String[] {"/api/projects/" + project, "/api/projects/" + project + "/members", "/api/scenes/" + scene,
                "/api/locations/" + location, "/api/projects/" + project + "/schedule"}) {
            grace.client().get().uri(uri).exchange().expectStatus().isNotFound();
        }
        grace.client().delete().uri("/api/projects/" + project).exchange().expectStatus().isNotFound();
        grace.client().get().uri("/api/projects").exchange().expectBody().jsonPath("$.items").isEmpty();
    }

    @Test
    void aViewerReadsEverythingAndChangesNothing() {
        Account ada = register("Ada");
        Account vera = register("Vera");
        String project = project(ada);
        String scene = scene(ada, project);
        String location = location(ada, scene);
        add(ada, project, vera, "VIEWER");

        vera.client().get().uri("/api/projects").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.items[0].id").isEqualTo(project).jsonPath("$.items[0].role").isEqualTo("VIEWER");
        vera.client().get().uri("/api/scenes/" + scene).exchange().expectStatus().isOk();
        vera.client().get().uri("/api/scenes/" + scene + "/locations").exchange().expectStatus().isOk();
        vera.client().get().uri("/api/locations/" + location).exchange().expectStatus().isOk();

        expectForbidden(vera.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("title", "Roof", "sourceText", "EXT. ROOF - DAY")).exchange());
        expectForbidden(vera.client().put().uri("/api/locations/" + location).bodyValue(Map.of("status", "SHORTLISTED")).exchange());
        expectForbidden(vera.client().delete().uri("/api/scenes/" + scene).exchange());
        expectForbidden(vera.client().put().uri("/api/projects/" + project).bodyValue(projectBody("Renamed", "ACTIVE")).exchange());
        expectForbidden(vera.client().post().uri("/api/projects/" + project + "/call-sheet-link").exchange());
    }

    @Test
    void anEditorChangesTheWorkButNotTheProjectsFateOrItsCrew() {
        Account ada = register("Ada");
        Account eddie = register("Eddie");
        String project = project(ada);
        add(ada, project, eddie, "EDITOR");
        // The crew's names come with the project, the owner first, for its avatars.
        eddie.client().get().uri("/api/projects?status=ACTIVE").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.items[0].crew[0]").isEqualTo("Ada").jsonPath("$.items[0].crew[1]").isEqualTo("Eddie");

        String scene = scene(eddie, project);
        String location = location(eddie, scene);
        eddie.client().put().uri("/api/locations/" + location).bodyValue(Map.of("status", "SHORTLISTED"))
                .exchange().expectStatus().isOk();
        eddie.client().put().uri("/api/projects/" + project).bodyValue(projectBody("Renamed", "ACTIVE"))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.title").isEqualTo("Renamed");

        expectForbidden(eddie.client().put().uri("/api/projects/" + project).bodyValue(projectBody("Renamed", "ARCHIVED")).exchange());
        expectForbidden(eddie.client().delete().uri("/api/projects/" + project).exchange());
        expectForbidden(eddie.client().post().uri("/api/projects/" + project + "/members")
                .bodyValue(Map.of("email", "someone@example.com", "role", "VIEWER")).exchange());
    }

    @Test
    void theOwnerChangesRolesRemovesMembersAndCannotBeAddedTwiceOrMadeASecondOwner() {
        Account ada = register("Ada");
        Account vera = register("Vera");
        String project = project(ada);
        add(ada, project, vera, "VIEWER");

        ada.client().post().uri("/api/projects/" + project + "/members").bodyValue(Map.of("email", vera.email(), "role", "EDITOR"))
                .exchange().expectStatus().isEqualTo(409);
        ada.client().post().uri("/api/projects/" + project + "/members").bodyValue(Map.of("email", "new@example.com", "role", "OWNER"))
                .exchange().expectStatus().isBadRequest();
        ada.client().put().uri("/api/projects/" + project + "/members/" + vera.id()).bodyValue(Map.of("role", "EDITOR"))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.role").isEqualTo("EDITOR");
        scene(vera, project);

        ada.client().delete().uri("/api/projects/" + project + "/members/" + vera.id()).exchange().expectStatus().isNoContent();
        vera.client().get().uri("/api/projects/" + project).exchange().expectStatus().isNotFound();
    }

    @Test
    void aMemberCanLeaveButTheOwnerMustHandOverFirst() {
        Account ada = register("Ada");
        Account vera = register("Vera");
        String project = project(ada);
        add(ada, project, vera, "VIEWER");

        ada.client().delete().uri("/api/projects/" + project + "/members/" + ada.id()).exchange().expectStatus().isEqualTo(409);
        vera.client().delete().uri("/api/projects/" + project + "/members/" + vera.id()).exchange().expectStatus().isNoContent();
        vera.client().get().uri("/api/projects/" + project).exchange().expectStatus().isNotFound();
    }

    @Test
    void handingOwnershipOverMakesTheOldOwnerAnEditor() {
        Account ada = register("Ada");
        Account eddie = register("Eddie");
        String project = project(ada);
        add(ada, project, eddie, "EDITOR");

        JsonNode crew = json(ada.client().post().uri("/api/projects/" + project + "/transfer").bodyValue(Map.of("userId", eddie.id()))
                .exchange().expectStatus().isOk());

        assertThat(crew.path("members").get(0).path("userId").asText()).isEqualTo(eddie.id().toString());
        assertThat(crew.path("members").get(0).path("role").asText()).isEqualTo("OWNER");
        expectForbidden(ada.client().delete().uri("/api/projects/" + project).exchange());
        eddie.client().delete().uri("/api/projects/" + project).exchange().expectStatus().isNoContent();
    }

    // --- invites --------------------------------------------------------------------------------------

    private String invite(Account owner, String projectId, String email, String role) {
        JsonNode added = json(owner.client().post().uri("/api/projects/" + projectId + "/members")
                .bodyValue(Map.of("email", email, "role", role)).exchange().expectStatus().isCreated());
        assertThat(added.path("member").isNull()).isTrue();
        assertThat(added.path("invite").path("email").asText()).isEqualTo(email.toLowerCase());
        return added.path("invite").path("token").asText();
    }

    @Test
    void someoneWithoutAnAccountGetsAnInviteLinkThatWorksOnceForTheirEmailOnly() {
        Account ada = register("Ada");
        String project = project(ada);
        String email = uniqueEmail("Nora");
        String token = invite(ada, project, email, "EDITOR");

        Account other = register("Other");
        other.client().get().uri("/api/invites/" + token).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.projectTitle").isEqualTo("The Night Ferry").jsonPath("$.state").isEqualTo("OPEN")
                .jsonPath("$.invitedBy").isEqualTo("Ada");
        expectForbidden(other.client().post().uri("/api/invites/" + token + "/accept").exchange());

        web.post().uri("/api/auth/register").bodyValue(Map.of("email", email, "password", PASSWORD, "displayName", "Nora"))
                .exchange().expectStatus().isCreated();
        var nora = as(email, PASSWORD);
        nora.post().uri("/api/invites/" + token + "/accept").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.state").isEqualTo("ACCEPTED").jsonPath("$.projectId").isEqualTo(project);
        nora.get().uri("/api/projects/" + project).exchange().expectStatus().isOk().expectBody().jsonPath("$.role").isEqualTo("EDITOR");
        nora.post().uri("/api/invites/" + token + "/accept").exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void anExpiredOrWithdrawnInviteNoLongerWorksAndOnlyTheOwnerSeesOpenInvites() {
        Account ada = register("Ada");
        Account vera = register("Vera");
        String project = project(ada);
        add(ada, project, vera, "VIEWER");
        String email = uniqueEmail("Nora");
        String token = invite(ada, project, email, "VIEWER");

        JsonNode crew = json(ada.client().get().uri("/api/projects/" + project + "/members").exchange().expectStatus().isOk());
        assertThat(crew.path("invites")).hasSize(1);
        assertThat(crew.path("invites").get(0).path("token").isNull()).isTrue(); // only ever shown when made
        vera.client().get().uri("/api/projects/" + project + "/members").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.invites").isEmpty().jsonPath("$.members.length()").isEqualTo(2);

        jdbc.update("UPDATE project_invites SET expires_at = now() - interval '1 minute'");
        web.post().uri("/api/auth/register").bodyValue(Map.of("email", email, "password", PASSWORD, "displayName", "Nora"))
                .exchange().expectStatus().isCreated();
        as(email, PASSWORD).post().uri("/api/invites/" + token + "/accept").exchange().expectStatus().isEqualTo(409);

        String second = invite(ada, project, uniqueEmail("Otto"), "VIEWER");
        String inviteId = json(ada.client().get().uri("/api/projects/" + project + "/members").exchange()).path("invites").get(0).path("id").asText();
        ada.client().delete().uri("/api/projects/" + project + "/invites/" + inviteId).exchange().expectStatus().isNoContent();
        as(email, PASSWORD).get().uri("/api/invites/" + second).exchange().expectStatus().isNotFound();
        as(email, PASSWORD).get().uri("/api/invites/not-a-token").exchange().expectStatus().isNotFound();
    }

    @Test
    void theInviteTokenIsNeverStored() {
        Account ada = register("Ada");
        String token = invite(ada, project(ada), uniqueEmail("Nora"), "VIEWER");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_invites WHERE token_hash = ?", Integer.class, token)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_invites", Integer.class)).isEqualTo(1);
    }
}
