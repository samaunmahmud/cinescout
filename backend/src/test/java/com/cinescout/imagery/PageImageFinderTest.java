package com.cinescout.imagery;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class PageImageFinderTest {

    @RegisterExtension
    static WireMockExtension site = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    /** The test site is on localhost, which the real check refuses; these tests are about everything else. */
    private final PageImageFinder finder = new PageImageFinder(WebClient.builder(), "CineScout-test", host -> true);

    private void page(String path, int status, String type, String body) {
        site.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(status).withHeader("Content-Type", type).withBody(body)));
    }

    @Test
    void readsTheSharingImageOfAPageAndSaysWhoIsAsking() {
        page("/roof", 200, "text/html; charset=utf-8", "<head><meta property=\"og:image\" content=\"/img/roof.jpg\"></head>");

        assertThat(finder.imageOf(site.baseUrl() + "/roof").block()).isEqualTo(site.baseUrl() + "/img/roof.jpg");
        site.verify(getRequestedFor(urlEqualTo("/roof")).withHeader("User-Agent", com.github.tomakehurst.wiremock.client.WireMock.equalTo("CineScout-test")));
    }

    @Test
    void givesNothingForAFailureAPageThatIsNotHtmlOrARedirect() {
        page("/gone", 404, "text/html", "<meta property=\"og:image\" content=\"https://cdn.example/a.jpg\">");
        page("/data", 200, "application/json", "{\"og:image\":\"https://cdn.example/a.jpg\"}");
        // Followed, the redirect would reach a page with a picture: it is not followed, as it could lead anywhere.
        page("/elsewhere", 200, "text/html", "<meta property=\"og:image\" content=\"https://cdn.example/a.jpg\">");
        site.stubFor(get(urlEqualTo("/moved")).willReturn(aResponse().withStatus(302).withHeader("Location", site.baseUrl() + "/elsewhere")));

        assertThat(finder.imageOf(site.baseUrl() + "/gone").block()).isNull();
        assertThat(finder.imageOf(site.baseUrl() + "/data").block()).isNull();
        assertThat(finder.imageOf(site.baseUrl() + "/moved").block()).isNull();
        site.verify(0, getRequestedFor(urlEqualTo("/elsewhere")));
    }

    @Test
    void givesNothingForAPageTooLargeToRead() {
        page("/huge", 200, "text/html", "<!--" + "x".repeat(1_100_000) + "--><meta property=\"og:image\" content=\"https://cdn.example/a.jpg\">");

        assertThat(finder.imageOf(site.baseUrl() + "/huge").block()).isNull();
    }

    @Test
    void neverAsksAHostThatIsNotPublicNorAnAddressThatIsNotAWebAddress() {
        page("/roof", 200, "text/html", "<meta property=\"og:image\" content=\"https://cdn.example/a.jpg\">");
        PageImageFinder guarded = new PageImageFinder(WebClient.builder(), "CineScout-test", PublicAddresses::isPublic);

        assertThat(guarded.imageOf(site.baseUrl() + "/roof").block()).isNull();
        assertThat(finder.imageOf("file:///etc/passwd").block()).isNull();
        assertThat(finder.imageOf("not a url at all").block()).isNull();
        site.verify(0, getRequestedFor(urlEqualTo("/roof")));
    }
}
