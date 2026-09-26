package com.cinescout.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCookiesTest {

    private static MockServerWebExchange over(String scheme) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(scheme + "://cinescout.example/api/auth/login"));
    }

    @Test
    void isSecureWhenTheRequestCameOverHttpsUnlessConfiguredOtherwise() {
        assertThat(new SessionCookies(null).issue(over("https"), "t", Duration.ofDays(1)).isSecure()).isTrue();
        assertThat(new SessionCookies(null).issue(over("http"), "t", Duration.ofDays(1)).isSecure()).isFalse();
        assertThat(new SessionCookies(true).issue(over("http"), "t", Duration.ofDays(1)).isSecure()).isTrue();
        assertThat(new SessionCookies(false).issue(over("https"), "t", Duration.ofDays(1)).isSecure()).isFalse();
    }

    @Test
    void theTokenIsReadOnlyTogetherWithTheScriptHeader() {
        MockServerHttpRequest withHeader = MockServerHttpRequest.get("/api/x").header("X-Requested-With", "XMLHttpRequest")
                .cookie(new HttpCookie(SessionCookies.NAME, "abc")).build();
        MockServerHttpRequest withoutHeader = MockServerHttpRequest.get("/api/x")
                .cookie(new HttpCookie(SessionCookies.NAME, "abc")).build();

        assertThat(SessionCookies.token(withHeader)).isEqualTo("abc");
        assertThat(SessionCookies.token(withoutHeader)).isNull();
    }
}
