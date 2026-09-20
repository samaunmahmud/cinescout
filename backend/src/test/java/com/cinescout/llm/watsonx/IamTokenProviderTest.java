package com.cinescout.llm.watsonx;

import com.cinescout.llm.LlmException;
import com.cinescout.llm.LlmException.Kind;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamTokenProviderTest {

    @RegisterExtension
    static WireMockExtension iam = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private IamTokenProvider provider() {
        return new IamTokenProvider(WebClient.create(iam.baseUrl()), "my-key");
    }

    private static void stubToken(String token, int expiresIn) {
        iam.stubFor(post("/identity/token").willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"" + token + "\",\"refresh_token\":\"ignored\",\"token_type\":\"Bearer\","
                        + "\"expires_in\":" + expiresIn + ",\"expiration\":1}")));
    }

    private static void verifyExchanges(int count) {
        iam.verify(count, postRequestedFor(urlEqualTo("/identity/token")));
    }

    private static void assertFailsWith(Kind kind, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(kind));
    }

    @Test
    void exchangesTheApiKeyUsingIbmsApiKeyGrant() {
        stubToken("tok-1", 3600);

        assertThat(provider().token().block()).isEqualTo("tok-1");

        iam.verify(postRequestedFor(urlEqualTo("/identity/token"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withRequestBody(containing("grant_type=urn%3Aibm%3Aparams%3Aoauth%3Agrant-type%3Aapikey"))
                .withRequestBody(containing("apikey=my-key")));
    }

    @Test
    void reusesTheTokenWhileItIsFresh() {
        stubToken("tok-1", 3600);
        IamTokenProvider provider = provider();

        provider.token().block();
        provider.token().block();

        verifyExchanges(1);
    }

    @Test
    void doesNotKeepATokenThatIsAboutToExpire() {
        stubToken("short-lived", 30);
        IamTokenProvider provider = provider();

        provider.token().block();
        provider.token().block();

        verifyExchanges(2);
    }

    @Test
    void aBurstOfCallersSharesOneExchange() {
        iam.stubFor(post("/identity/token").willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withFixedDelay(200)
                .withBody("{\"access_token\":\"tok-1\",\"expires_in\":3600}")));
        IamTokenProvider provider = provider();

        assertThat(Flux.range(0, 8).flatMap(i -> provider.token()).collectList().block())
                .hasSize(8).containsOnly("tok-1");

        verifyExchanges(1);
    }

    @Test
    void invalidateForcesANewExchange() {
        stubToken("tok-1", 3600);
        IamTokenProvider provider = provider();
        provider.token().block();

        provider.invalidate();
        provider.token().block();

        verifyExchanges(2);
    }

    @Test
    void aRejectedKeyIsAnAuthenticationFailureAndIsNotCached() {
        iam.stubFor(post("/identity/token").willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"errorCode\":\"BXNIM0415E\",\"errorMessage\":\"Provided API key could not be found.\"}")));
        IamTokenProvider provider = provider();

        assertFailsWith(Kind.AUTHENTICATION, () -> provider.token().block());

        stubToken("tok-after-fix", 3600);
        assertThat(provider.token().block()).isEqualTo("tok-after-fix");
    }

    @Test
    void anIamOutageIsRetryableUnavailable() {
        iam.stubFor(post("/identity/token").willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> provider().token().block())
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE);
                    assertThat(e.isRetryable()).isTrue();
                });
    }

    @Test
    void aResponseWithoutATokenIsRejected() {
        iam.stubFor(post("/identity/token").willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"expires_in\":3600}")));

        assertFailsWith(Kind.UNAVAILABLE, () -> provider().token().block());
    }

    @Test
    void errorsNeverContainTheApiKey() {
        iam.stubFor(post("/identity/token").willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> provider().token().block()).hasMessageNotContaining("my-key");
    }
}
