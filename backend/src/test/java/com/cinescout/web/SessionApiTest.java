package com.cinescout.web;

import com.cinescout.security.SessionCookies;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The web app's cookie login, through the whole filter chain. */
class SessionApiTest extends ApiTest {

    private static final String XHR = "X-Requested-With";

    private EntityExchangeResult<byte[]> logIn(String email, String password) {
        return web.post().uri("/api/auth/login").header(XHR, "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email, "password", password))
                .exchange()
                .expectBody().returnResult();
    }

    private String sessionToken(EntityExchangeResult<byte[]> login) {
        ResponseCookie cookie = login.getResponseCookies().getFirst(SessionCookies.NAME);
        assertThat(cookie).as("session cookie").isNotNull();
        return cookie.getValue();
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec me(String token) {
        return web.get().uri("/api/auth/me").header(XHR, "XMLHttpRequest").cookie(SessionCookies.NAME, token).exchange();
    }

    private static String sha256(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void loggingInSetsAnHttpOnlyStrictCookieThatLogsTheWebAppIn() {
        Account ada = register("Ada");

        EntityExchangeResult<byte[]> login = logIn(ada.email().toUpperCase(), PASSWORD);

        assertThat(login.getStatus().value()).isEqualTo(200);
        assertThat(new String(login.getResponseBodyContent())).contains(ada.id().toString()).doesNotContain(PASSWORD);
        ResponseCookie cookie = login.getResponseCookies().getFirst(SessionCookies.NAME);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api");
        assertThat(cookie.getMaxAge().toDays()).isEqualTo(14);
        assertThat(cookie.isSecure()).as("plain HTTP here, so not Secure").isFalse();
        assertThat(cookie.getValue()).hasSizeGreaterThanOrEqualTo(43);

        me(cookie.getValue()).expectStatus().isOk().expectBody().jsonPath("$.email").isEqualTo(ada.email());
        web.get().uri("/api/projects").header(XHR, "XMLHttpRequest").cookie(SessionCookies.NAME, cookie.getValue())
                .exchange().expectStatus().isOk();
    }

    @Test
    void onlyAHashOfTheTokenIsStored() throws Exception {
        Account ada = register("Ada");
        String token = sessionToken(logIn(ada.email(), PASSWORD));

        String stored = jdbc.queryForObject("SELECT token_hash FROM auth_sessions WHERE user_id = ?", String.class, ada.id());
        assertThat(stored).isEqualTo(sha256(token)).isNotEqualTo(token);
    }

    @Test
    void theCookieDoesNotCountWithoutTheScriptHeaderSoOtherSitesCannotRideOnIt() {
        Account ada = register("Ada");
        String token = sessionToken(logIn(ada.email(), PASSWORD));

        web.get().uri("/api/auth/me").cookie(SessionCookies.NAME, token).exchange().expectStatus().isUnauthorized();
        web.post().uri("/api/projects").cookie(SessionCookies.NAME, token)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("title", "Forged"))
                .exchange().expectStatus().isUnauthorized();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM projects", Integer.class)).isZero();
    }

    @Test
    void aWrongPasswordAnUnknownEmailAndADisabledAccountGetTheSame401AndNoCookie() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        jdbc.update("UPDATE users SET enabled = false WHERE id = ?", grace.id());

        EntityExchangeResult<byte[]> wrong = logIn(ada.email(), "not-the-password");
        EntityExchangeResult<byte[]> unknown = logIn("nobody@example.com", PASSWORD);
        EntityExchangeResult<byte[]> disabled = logIn(grace.email(), PASSWORD);

        for (EntityExchangeResult<byte[]> result : java.util.List.of(wrong, unknown, disabled)) {
            assertThat(result.getStatus().value()).isEqualTo(401);
            assertThat(result.getResponseCookies().getFirst(SessionCookies.NAME)).isNull();
            assertThat(new String(result.getResponseBodyContent())).isEqualTo(new String(wrong.getResponseBodyContent()));
        }
        assertThat(new String(wrong.getResponseBodyContent())).contains("The email or password is not right");
    }

    @Test
    void anInvalidLoginRequestIsA400ProblemThatNeverQuotesThePassword() {
        web.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", "", "password", "secret-value"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class).value(body -> assertThat(body).contains("email").doesNotContain("secret-value"));
    }

    @Test
    void loggingOutEndsTheSessionAndClearsTheCookie() {
        Account ada = register("Ada");
        String token = sessionToken(logIn(ada.email(), PASSWORD));

        EntityExchangeResult<byte[]> logout = web.post().uri("/api/auth/logout").header(XHR, "XMLHttpRequest")
                .cookie(SessionCookies.NAME, token).exchange().expectStatus().isNoContent().expectBody().returnResult();

        ResponseCookie cleared = logout.getResponseCookies().getFirst(SessionCookies.NAME);
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getValue()).isEmpty();
        me(token).expectStatus().isUnauthorized();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class)).isZero();
        // Logging out again, or without a session, is harmless.
        web.post().uri("/api/auth/logout").header(XHR, "XMLHttpRequest").cookie(SessionCookies.NAME, token)
                .exchange().expectStatus().isNoContent();
        web.post().uri("/api/auth/logout").exchange().expectStatus().isNoContent();
    }

    @Test
    void anExpiredSessionNoLongerWorksAndIsPurgedAtTheNextLogin() {
        Account ada = register("Ada");
        String token = sessionToken(logIn(ada.email(), PASSWORD));
        jdbc.update("UPDATE auth_sessions SET created_at = now() - interval '20 days', expires_at = now() - interval '1 day'");

        me(token).expectStatus().isUnauthorized();

        String fresh = sessionToken(logIn(ada.email(), PASSWORD));
        assertThat(jdbc.queryForList("SELECT token_hash FROM auth_sessions", String.class)).hasSize(1);
        me(fresh).expectStatus().isOk();
    }

    @Test
    void disablingAnAccountEndsItsSessionsAtOnce() {
        Account ada = register("Ada");
        String token = sessionToken(logIn(ada.email(), PASSWORD));
        jdbc.update("UPDATE users SET enabled = false WHERE id = ?", ada.id());

        me(token).expectStatus().isUnauthorized();
    }

    @Test
    void aStaleCookieDoesNotStopSomeoneLoggingInOrRegistering() {
        Account ada = register("Ada");
        String stale = UUID.randomUUID().toString();

        web.post().uri("/api/auth/login").header(XHR, "XMLHttpRequest").cookie(SessionCookies.NAME, stale)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("email", ada.email(), "password", PASSWORD))
                .exchange().expectStatus().isOk();
        web.post().uri("/api/auth/register").header(XHR, "XMLHttpRequest").cookie(SessionCookies.NAME, stale)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", uniqueEmail("Grace"), "password", PASSWORD, "displayName", "Grace"))
                .exchange().expectStatus().isCreated();
        me(stale).expectStatus().isUnauthorized();
    }

    @Test
    void oneAccountsSessionNeverReachesAnothersData() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String graceToken = sessionToken(logIn(grace.email(), PASSWORD));
        String projectId = ada.client().post().uri("/api/projects").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("title", "Ada's film")).exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody().get("id").toString();

        web.get().uri("/api/projects/" + projectId).header(XHR, "XMLHttpRequest").cookie(SessionCookies.NAME, graceToken)
                .exchange().expectStatus().isNotFound();
    }
}
