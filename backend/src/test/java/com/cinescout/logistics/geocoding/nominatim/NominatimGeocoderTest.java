package com.cinescout.logistics.geocoding.nominatim;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import com.cinescout.logistics.ProviderHttp;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** The match body is a trimmed copy of a real Nominatim answer (captured 2026-09-22). */
class NominatimGeocoderTest {

    private static final String MATCH = """
            [{"place_id":359015236,"licence":"Data © OpenStreetMap contributors, ODbL 1.0.","osm_type":"way",
              "lat":"40.6982888","lon":"-73.9999297","category":"leisure","type":"park","name":"Brooklyn Bridge Park",
              "display_name":"Brooklyn Bridge Park, 334, Brooklyn, Kings County, New York, 11201, United States"}]""";

    @RegisterExtension
    static WireMockExtension api = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private NominatimGeocoder geocoder() {
        Duration timeout = Duration.ofSeconds(5);
        return new NominatimGeocoder(ProviderHttp.webClient(WebClient.builder(), api.baseUrl(), timeout, "CineScout-test"),
                new NominatimProperties(api.baseUrl(), timeout, Duration.ZERO, Duration.ZERO));
    }

    private void stub(int status, String body) {
        api.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    void theBestMatchsStringCoordinatesAreParsed() {
        stub(200, MATCH);

        assertThat(geocoder().locate("Brooklyn Bridge Park, Brooklyn, New York").block())
                .isEqualTo(new GeoPoint(40.6982888, -73.9999297));
    }

    @Test
    void theQueryIsEncodedAndOnlyOneMatchIsAskedFor() {
        stub(200, MATCH);

        geocoder().locate("  Joe's Bar & Grill, 12 Main St, Brooklyn  ").block();

        api.verify(getRequestedFor(urlPathEqualTo("/search"))
                .withQueryParam("q", equalTo("Joe's Bar & Grill, 12 Main St, Brooklyn"))
                .withQueryParam("format", equalTo("jsonv2"))
                .withQueryParam("limit", equalTo("1"))
                .withHeader("User-Agent", equalTo("CineScout-test")));
    }

    @Test
    void noMatchIsEmpty() {
        stub(200, "[]");

        assertThat(geocoder().locate("Nowhere in particular").blockOptional()).isEmpty();
    }

    @Test
    void aMatchWithUnusableCoordinatesIsNoMatch() {
        stub(200, "[{\"lat\":\"north-ish\",\"lon\":\"-73.99\"}]");
        assertThat(geocoder().locate("x").blockOptional()).isEmpty();

        stub(200, "[{\"lat\":\"91.5\",\"lon\":\"-73.99\"}]");
        assertThat(geocoder().locate("x").blockOptional()).isEmpty();

        stub(200, "[{\"display_name\":\"no coordinates\"}]");
        assertThat(geocoder().locate("x").blockOptional()).isEmpty();
    }

    @Test
    void aBlankQueryIsNotSent() {
        assertThat(geocoder().locate("  ").blockOptional()).isEmpty();
        assertThat(geocoder().locate(null).blockOptional()).isEmpty();
        api.verify(0, getRequestedFor(urlPathEqualTo("/search")));
    }

    @Test
    void aBlockedUserAgentIsARejectedRequestAndAnOutageIsUnavailable() {
        stub(403, "<html>Access blocked</html>");
        assertThat(((LogisticsException) catchThrowable(() -> geocoder().locate("x").block())).kind()).isEqualTo(Kind.INVALID_REQUEST);

        stub(503, "<html>Service Unavailable</html>");
        assertThat(((LogisticsException) catchThrowable(() -> geocoder().locate("x").block())).kind()).isEqualTo(Kind.UNAVAILABLE);
    }
}
