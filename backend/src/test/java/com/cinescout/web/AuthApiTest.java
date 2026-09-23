package com.cinescout.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthApiTest extends ApiTest {

    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;

    private String bodyOf(org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec response) {
        return new String(response.expectBody().returnResult().getResponseBodyContent());
    }

    // --- registration ---------------------------------------------------------------------------

    @Test
    void registeringCreatesARegularAccountAndNeverEchoesTheSecret() {
        String email = uniqueEmail("Ada");

        String body = bodyOf(web.post().uri("/api/auth/register")
                .bodyValue(Map.of("email", email, "password", PASSWORD, "displayName", " Ada Lovelace "))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        assertThat(body).contains(email).contains("\"displayName\":\"Ada Lovelace\"").contains("\"role\":\"USER\"")
                .doesNotContain(PASSWORD).doesNotContain("password").doesNotContain("bcrypt");
        String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(stored).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }

    @Test
    void theRoleCanNeverBeChosenAtRegistration() {
        String email = uniqueEmail("mallory");

        web.post().uri("/api/auth/register")
                .bodyValue(Map.of("email", email, "password", PASSWORD, "displayName", "Mallory", "role", "ADMIN", "enabled", false))
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.role").isEqualTo("USER");
        assertThat(jdbc.queryForObject("SELECT enabled FROM users WHERE email = ?", Boolean.class, email)).isTrue();
    }

    @Test
    void anEmailCanOnlyBeRegisteredOnceIgnoringCase() {
        String email = uniqueEmail("grace");
        web.post().uri("/api/auth/register").bodyValue(Map.of("email", email, "password", PASSWORD, "displayName", "Grace"))
                .exchange().expectStatus().isCreated();

        web.post().uri("/api/auth/register")
                .bodyValue(Map.of("email", email.toUpperCase(), "password", PASSWORD, "displayName", "Impostor"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentType(PROBLEM)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Conflict")
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.detail").isEqualTo("That email address is already registered");
    }

    @Test
    void invalidRegistrationsListTheFieldsToFixWithoutQuotingAnyValue() {
        String body = bodyOf(web.post().uri("/api/auth/register")
                .bodyValue(Map.of("email", "not-an-email", "password", "s3cr3t!", "displayName", " "))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(PROBLEM));

        assertThat(body).contains("Validation failed").contains("\"field\":\"email\"").contains("\"field\":\"password\"")
                .contains("\"field\":\"displayName\"")
                .doesNotContain("s3cr3t!").doesNotContain("not-an-email");
    }

    @Test
    void malformedJsonIsABadRequestProblem() {
        web.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"email\": ")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(PROBLEM)
                .expectBody().jsonPath("$.status").isEqualTo(400);
    }

    // --- logging in -----------------------------------------------------------------------------

    @Test
    void meReturnsTheAccountTheCredentialsBelongTo() {
        Account ada = register("Ada");

        ada.client().get().uri("/api/auth/me").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(ada.id().toString())
                .jsonPath("$.email").isEqualTo(ada.email())
                .jsonPath("$.displayName").isEqualTo("Ada");
    }

    @Test
    void theEmailIsMatchedIgnoringCaseWhenLoggingIn() {
        Account ada = register("Ada");

        as(ada.email().toUpperCase(), PASSWORD).get().uri("/api/auth/me").exchange().expectStatus().isOk();
    }

    @Test
    void everythingButRegistrationNeedsALogin() {
        web.get().uri("/api/auth/me").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"CineScout\", charset=\"UTF-8\"")
                .expectHeader().contentType(PROBLEM)
                .expectBody().jsonPath("$.title").isEqualTo("Unauthorized");
        web.get().uri("/api/projects").exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/anything-at-all").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void theWebAppGetsTheUnauthorizedProblemWithoutTheBrowserLoginChallenge() {
        Account ada = register("Ada");

        as(ada.email(), "not-the-password").get().uri("/api/auth/me").header("X-Requested-With", "XMLHttpRequest")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectHeader().contentType(PROBLEM)
                .expectBody().jsonPath("$.title").isEqualTo("Unauthorized");
    }

    @Test
    void aWrongPasswordAndAnUnknownAccountAreIndistinguishable() {
        Account ada = register("Ada");

        String wrongPassword = bodyOf(as(ada.email(), "not-the-password").get().uri("/api/auth/me").exchange()
                .expectStatus().isUnauthorized().expectHeader().contentType(PROBLEM));
        String unknownAccount = bodyOf(as("nobody@example.com", PASSWORD).get().uri("/api/auth/me").exchange()
                .expectStatus().isUnauthorized().expectHeader().contentType(PROBLEM));

        assertThat(wrongPassword).isEqualTo(unknownAccount);
    }

    @Test
    void aDisabledAccountCannotLogIn() {
        Account ada = register("Ada");
        jdbc.update("UPDATE users SET enabled = false WHERE id = ?", ada.id());

        ada.client().get().uri("/api/auth/me").exchange().expectStatus().isUnauthorized();
    }

    // --- other errors are problems too ------------------------------------------------------------

    @Test
    void anUnknownRouteIsA404ProblemForALoggedInUser() {
        register("Ada").client().get().uri("/api/nothing-here").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(PROBLEM)
                .expectBody().jsonPath("$.status").isEqualTo(404);
    }

    @Test
    void aWrongMethodIsA405Problem() {
        register("Ada").client().delete().uri("/api/auth/me").exchange()
                .expectStatus().isEqualTo(405)
                .expectHeader().contentType(PROBLEM);
    }
}
