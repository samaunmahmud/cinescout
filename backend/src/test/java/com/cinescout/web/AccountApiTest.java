package com.cinescout.web;

import com.cinescout.security.SessionCookies;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The account endpoints through the whole filter chain. */
class AccountApiTest extends ApiTest {

    private static final String XHR = "X-Requested-With";
    private static final String NEW_PASSWORD = "a-brand-new-password";

    private String logIn(String email, String password) {
        return web.post().uri("/api/auth/login").header(XHR, "XMLHttpRequest")
                .bodyValue(Map.of("email", email, "password", password))
                .exchange().expectStatus().isOk()
                .expectBody().returnResult().getResponseCookies().getFirst(SessionCookies.NAME).getValue();
    }

    private WebTestClient withSession(String token) {
        return web.mutate().defaultHeader(XHR, "XMLHttpRequest").defaultCookie(SessionCookies.NAME, token).build();
    }

    private static String errorField(JsonNode problem) {
        return problem.path("errors").get(0).path("field").asText();
    }

    @Test
    void theDisplayNameCanBeChangedAndIsTrimmed() {
        Account ada = register("Ada");

        ada.client().put().uri("/api/account").bodyValue(Map.of("displayName", "  Ada Lovelace "))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.displayName").isEqualTo("Ada Lovelace").jsonPath("$.email").isEqualTo(ada.email());
        ada.client().get().uri("/api/auth/me").exchange().expectBody().jsonPath("$.displayName").isEqualTo("Ada Lovelace");

        JsonNode problem = ada.client().put().uri("/api/account").bodyValue(Map.of("displayName", " "))
                .exchange().expectStatus().isBadRequest().expectBody(JsonNode.class).returnResult().getResponseBody();
        assertThat(errorField(problem)).isEqualTo("displayName");
    }

    @Test
    void aSharedDemoAccountKeepsItsNameAndPasswordAndCannotBeDeleted() {
        String email = "shared-demo@example.com"; // listed in the test config in other capitals
        web.post().uri("/api/auth/register")
                .bodyValue(Map.of("email", email, "password", PASSWORD, "displayName", "Demo"))
                .exchange().expectStatus().isCreated();
        WebTestClient demo = as(email, PASSWORD);

        demo.put().uri("/api/account").bodyValue(Map.of("displayName", "Taken over"))
                .exchange().expectStatus().isForbidden();
        demo.put().uri("/api/account/password").bodyValue(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                .exchange().expectStatus().isForbidden();
        demo.post().uri("/api/account/delete").bodyValue(Map.of("password", PASSWORD))
                .exchange().expectStatus().isForbidden();

        demo.get().uri("/api/auth/me").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.displayName").isEqualTo("Demo");
        demo.get().uri("/api/projects").exchange().expectStatus().isOk();
    }

    @Test
    void changingThePasswordNeedsTheCurrentOneAndThenOnlyTheNewOneWorks() {
        Account ada = register("Ada");

        JsonNode wrong = ada.client().put().uri("/api/account/password")
                .bodyValue(Map.of("currentPassword", "not-my-password", "newPassword", NEW_PASSWORD))
                .exchange().expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(JsonNode.class).returnResult().getResponseBody();
        assertThat(errorField(wrong)).isEqualTo("currentPassword");
        assertThat(wrong.toString()).doesNotContain("not-my-password").doesNotContain(NEW_PASSWORD);
        ada.client().get().uri("/api/auth/me").exchange().expectStatus().isOk();

        ada.client().put().uri("/api/account/password")
                .bodyValue(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                .exchange().expectStatus().isNoContent();

        ada.client().get().uri("/api/auth/me").exchange().expectStatus().isUnauthorized();
        as(ada.email(), NEW_PASSWORD).get().uri("/api/auth/me").exchange().expectStatus().isOk();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, ada.id()))
                .startsWith("{bcrypt}$2").doesNotContain(NEW_PASSWORD);
    }

    @Test
    void aNewPasswordMustBeLongEnough() {
        Account ada = register("Ada");

        JsonNode problem = ada.client().put().uri("/api/account/password")
                .bodyValue(Map.of("currentPassword", PASSWORD, "newPassword", "short"))
                .exchange().expectStatus().isBadRequest().expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(errorField(problem)).isEqualTo("newPassword");
        ada.client().get().uri("/api/auth/me").exchange().expectStatus().isOk();
    }

    @Test
    void changingThePasswordLogsOutEveryOtherSessionButKeepsTheOneThatDidIt() {
        Account ada = register("Ada");
        String here = logIn(ada.email(), PASSWORD);
        String elsewhere = logIn(ada.email(), PASSWORD);

        withSession(here).put().uri("/api/account/password")
                .bodyValue(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                .exchange().expectStatus().isNoContent();

        withSession(here).get().uri("/api/auth/me").exchange().expectStatus().isOk();
        withSession(elsewhere).get().uri("/api/auth/me").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void deletingTheAccountNeedsThePasswordAndTakesEverythingWithIt() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String token = logIn(ada.email(), PASSWORD);
        ada.client().post().uri("/api/projects").bodyValue(Map.of("title", "Neon Nights")).exchange().expectStatus().isCreated();
        grace.client().post().uri("/api/projects").bodyValue(Map.of("title", "Grace's")).exchange().expectStatus().isCreated();

        JsonNode wrong = withSession(token).post().uri("/api/account/delete").bodyValue(Map.of("password", "not-my-password"))
                .exchange().expectStatus().isBadRequest().expectBody(JsonNode.class).returnResult().getResponseBody();
        assertThat(errorField(wrong)).isEqualTo("password");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM projects", Integer.class)).isEqualTo(2);

        var deleted = withSession(token).post().uri("/api/account/delete").bodyValue(Map.of("password", PASSWORD))
                .exchange().expectStatus().isNoContent().expectBody().returnResult();

        assertThat(deleted.getResponseCookies().getFirst(SessionCookies.NAME).getMaxAge().isZero()).as("cookie cleared").isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, ada.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM projects", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class)).isZero();
        withSession(token).get().uri("/api/auth/me").exchange().expectStatus().isUnauthorized();
        ada.client().get().uri("/api/auth/me").exchange().expectStatus().isUnauthorized();
        grace.client().get().uri("/api/auth/me").exchange().expectStatus().isOk();
    }

    @Test
    void theAccountEndpointsNeedALogin() {
        web.put().uri("/api/account").bodyValue(Map.of("displayName", "Nobody")).exchange().expectStatus().isUnauthorized();
        web.put().uri("/api/account/password").bodyValue(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                .exchange().expectStatus().isUnauthorized();
        web.post().uri("/api/account/delete").bodyValue(Map.of("password", PASSWORD)).exchange().expectStatus().isUnauthorized();
    }
}
