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

    // --- the area of a position ------------------------------------------------------------------

    /** A trimmed copy of a real answer for Camden Town at zoom 10 (captured 2026-10-04). */
    private static final String CAMDEN = """
            {"place_id":280603961,"osm_type":"relation","category":"boundary","type":"administrative","addresstype":"city_district",
             "name":"London Borough of Camden","display_name":"London Borough of Camden, Greater London, England, United Kingdom",
             "address":{"city_district":"London Borough of Camden","ISO3166-2-lvl8":"GB-CMD","city":"Greater London","state":"England",
                        "ISO3166-2-lvl4":"GB-ENG","country":"United Kingdom","country_code":"gb"}}""";

    @Test
    void theAreaOfAPositionIsItsMostLocalNamedAreaWithItsCodesMostLocalFirst() {
        api.stubFor(get(urlPathEqualTo("/reverse")).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(CAMDEN)));

        var area = geocoder().areaAt(new GeoPoint(51.539, -0.1426)).block();

        assertThat(area.name()).isEqualTo("London Borough of Camden");
        assertThat(area.codes()).containsExactly("GB-CMD", "GB-ENG");
        assertThat(area.countryCode()).isEqualTo("gb");
        assertThat(area.isFor(51.539, -0.1426)).isTrue();
        api.verify(getRequestedFor(urlPathEqualTo("/reverse")).withQueryParam("zoom", equalTo("10"))
                .withQueryParam("addressdetails", equalTo("1")).withQueryParam("lat", equalTo("51.539")));
    }

    @Test
    void aPositionNobodyKnowsHasNoArea() {
        api.stubFor(get(urlPathEqualTo("/reverse")).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"Unable to geocode\"}")));

        assertThat(geocoder().areaAt(new GeoPoint(0, 0)).block()).isNull();
    }

    @Test
    void aSpotIsNamedByItsNeighbourhoodTownAndCountryEachOnce() {
        api.stubFor(get(urlPathEqualTo("/reverse")).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                {"name":"Rivington Street","address":{"road":"Rivington Street","suburb":"Shoreditch","city_district":"London Borough of Hackney",
                 "city":"London","state":"England","postcode":"EC2A 3QQ","country":"United Kingdom","country_code":"gb"}}""")));

        assertThat(geocoder().placeAt(new GeoPoint(51.526, -0.078), "en-GB,en;q=0.9").block()).isEqualTo("Shoreditch, London, United Kingdom");
        api.verify(getRequestedFor(urlPathEqualTo("/reverse")).withQueryParam("zoom", equalTo("14"))
                .withQueryParam("accept-language", equalTo("en-GB,en;q=0.9")));

        geocoder().placeAt(new GeoPoint(51.526, -0.078), null).block();
        api.verify(getRequestedFor(urlPathEqualTo("/reverse")).withQueryParam("accept-language", equalTo("en")));
    }

    @Test
    void aTownWithoutNeighbourhoodsIsNamedByItselfAndASpotNobodyKnowsHasNoName() {
        assertThat(NominatimGeocoder.placeName(new NominatimGeocoder.Place("Hay-on-Wye",
                java.util.Map.of("town", "Hay-on-Wye", "county", "Powys", "country", "United Kingdom")))).isEqualTo("Hay-on-Wye, United Kingdom");
        assertThat(NominatimGeocoder.placeName(new NominatimGeocoder.Place(null, java.util.Map.of()))).isNull();
        assertThat(NominatimGeocoder.placeName(null)).isNull();
    }
}
