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
    private final PageImageFinder finder = new PageImageFinder(WebClient.builder(), "CineScout-test", host -> true, false);

    private void page(String path, int status, String type, String body) {
        site.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(status).withHeader("Content-Type", type).withBody(body)));
    }

    @Test
    void readsTheSharingImageOfAPageAndSaysWhoIsAsking() {
        page("/roof", 200, "text/html; charset=utf-8", "<head><meta property=\"og:image\" content=\"/img/roof.jpg\"></head>");

        assertThat(finder.imageOf(site.baseUrl() + "/roof").block()).isEqualTo(site.baseUrl().replace("http:", "https:") + "/img/roof.jpg");
        site.verify(getRequestedFor(urlEqualTo("/roof")).withHeader("User-Agent", com.github.tomakehurst.wiremock.client.WireMock.equalTo("CineScout-test")));
    }

    @Test
    void givesNothingForAFailureOrAPageThatIsNotHtml() {
        page("/gone", 404, "text/html", "<meta property=\"og:image\" content=\"https://cdn.example/a.jpg\">");
        page("/data", 200, "application/json", "{\"og:image\":\"https://cdn.example/a.jpg\"}");

        assertThat(finder.imageOf(site.baseUrl() + "/gone").block()).isNull();
        assertThat(finder.imageOf(site.baseUrl() + "/data").block()).isNull();
    }

    private void redirect(String from, String to) {
        site.stubFor(get(urlEqualTo(from)).willReturn(aResponse().withStatus(301).withHeader("Location", to)));
    }

    @Test
    void followsAFewRedirectsToThePage() {
        page("/new", 200, "text/html", "<meta property=\"og:image\" content=\"https://cdn.example/a.jpg\">");
        redirect("/old", "/older");
        redirect("/older", site.baseUrl() + "/new");

        assertThat(finder.imageOf(site.baseUrl() + "/old").block()).isEqualTo("https://cdn.example/a.jpg");
    }

    @Test
    void givesUpOnALongChainOfRedirectsOrALoop() {
        page("/end", 200, "text/html", "<meta property=\"og:image\" content=\"https://cdn.example/a.jpg\">");
        redirect("/r1", "/r2");
        redirect("/r2", "/r3");
        redirect("/r3", "/r4");
        redirect("/r4", "/end");
        redirect("/loop", "/loop");

        assertThat(finder.imageOf(site.baseUrl() + "/r1").block()).isNull();
        assertThat(finder.imageOf(site.baseUrl() + "/loop").block()).isNull();
        site.verify(0, getRequestedFor(urlEqualTo("/end")));
        site.verify(PageImageFinder.MAX_REDIRECTS + 1, getRequestedFor(urlEqualTo("/loop")));
    }

    @Test
    void aRedirectMustLeadToAHostThatPassesTheSameCheck() {
        page("/roof", 200, "text/html", "<meta property=\"og:image\" content=\"https://cdn.example/a.jpg\">");
        redirect("/away", "http://127.0.0.1:" + site.getPort() + "/roof");
        redirect("/file", "file:///etc/passwd");
        // Only "localhost" passes: the redirect names the same server by its address, which does not.
        PageImageFinder picky = new PageImageFinder(WebClient.builder(), "CineScout-test", host -> host.equals("localhost"), false);

        assertThat(picky.imageOf(site.baseUrl() + "/away").block()).isNull();
        assertThat(picky.imageOf(site.baseUrl() + "/file").block()).isNull();
        site.verify(0, getRequestedFor(urlEqualTo("/roof")));
    }

    @Test
    void givesNothingForAPageTooLargeToRead() {
        page("/huge", 200, "text/html", "<!--" + "x".repeat(1_100_000) + "--><meta property=\"og:image\" content=\"https://cdn.example/a.jpg\">");

        assertThat(finder.imageOf(site.baseUrl() + "/huge").block()).isNull();
    }

    @Test
    void neverAsksAHostThatIsNotPublicNorAnAddressThatIsNotAWebAddress() {
        page("/roof", 200, "text/html", "<meta property=\"og:image\" content=\"https://cdn.example/a.jpg\">");
        PageImageFinder guarded = new PageImageFinder(WebClient.builder(), "CineScout-test", PublicAddresses::isPublic, true);

        assertThat(guarded.imageOf(site.baseUrl() + "/roof").block()).isNull();
        assertThat(finder.imageOf("file:///etc/passwd").block()).isNull();
        assertThat(finder.imageOf("not a url at all").block()).isNull();
        site.verify(0, getRequestedFor(urlEqualTo("/roof")));
    }

    @Test
    void neverConnectsToALocalAddressEvenWhenTheNameLooksFineToTheFirstCheck() {
        page("/roof", 200, "text/html", "<meta property=\"og:image\" content=\"https://cdn.example/a.jpg\">");
        // As when a name resolves to a public address for the check and to a local one for the connection.
        PageImageFinder fooled = new PageImageFinder(WebClient.builder(), "CineScout-test", host -> true, true);

        assertThat(fooled.imageOf(site.baseUrl() + "/roof").block()).isNull();
        site.verify(0, getRequestedFor(urlEqualTo("/roof")));
    }
}
