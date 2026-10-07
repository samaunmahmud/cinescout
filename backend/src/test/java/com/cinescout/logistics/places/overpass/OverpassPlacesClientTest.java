package com.cinescout.logistics.places.overpass;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.places.Place;
import com.cinescout.logistics.places.PlaceKind;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;

/** Element shapes are those of real Overpass answers (captured 2026-09-22), with invented values. */
class OverpassPlacesClientTest {

    private static final GeoPoint ORIGIN = new GeoPoint(40.7128, -74.0060);
    private static final String INTERPRETER = "/api/interpreter";

    @RegisterExtension
    static WireMockExtension api = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private OverpassPlacesClient client() {
        Duration timeout = Duration.ofSeconds(5);
        return new OverpassPlacesClient(ProviderHttp.webClient(WebClient.builder(), api.baseUrl(), timeout, "CineScout-test"),
                new OverpassProperties(api.baseUrl(), 25, timeout, Duration.ofMillis(10), 1000));
    }

    private void stub(int status, String body) {
        api.stubFor(post(urlEqualTo(INTERPRETER)).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static String elements(String... elements) {
        return "{\"version\":0.6,\"elements\":[" + String.join(",", elements) + "]}";
    }

    @Test
    void nodesAndAreasAreClassifiedAndMeasuredFromTheLocation() {
        stub(200, elements(
                """
                {"type":"node","id":1,"lat":40.7138,"lon":-74.0060,"tags":{"amenity":"cafe","name":"Starbucks"}}""",
                """
                {"type":"way","id":2,"center":{"lat":40.7148,"lon":-74.0060},"tags":{"amenity":"parking"}}"""));

        List<Place> places = client().around(ORIGIN).block();

        // The car park is parking for the crew and a possible unit base.
        assertThat(places).extracting(Place::kind).containsExactly(PlaceKind.FOOD, PlaceKind.PARKING, PlaceKind.UNIT_BASE);
        Place cafe = places.getFirst();
        assertThat(cafe.kind()).isEqualTo(PlaceKind.FOOD);
        assertThat(cafe.name()).isEqualTo("Starbucks");
        assertThat(cafe.position()).isEqualTo(new GeoPoint(40.7138, -74.0060));
        assertThat(cafe.distanceMeters()).isCloseTo(111.2, within(1.0));
        Place parking = places.get(1);
        assertThat(parking.kind()).isEqualTo(PlaceKind.PARKING);
        assertThat(parking.name()).isNull();
        assertThat(parking.distanceMeters()).isCloseTo(222.4, within(1.0));
    }

    @Test
    void aLineIsMeasuredToItsNearestStretchAndHasNoPosition() {
        // A trunk road running east-west 0.001 degrees (111 m) north; both of its ends are over 800 m away.
        stub(200, elements("""
                {"type":"way","id":3,"bounds":{},"geometry":[{"lat":40.7138,"lon":-74.0160},{"lat":40.7138,"lon":-73.9960}],
                 "tags":{"highway":"trunk","ref":"NY 9A"}}"""));

        Place road = client().around(ORIGIN).block().getFirst();

        assertThat(road.kind()).isEqualTo(PlaceKind.MAJOR_ROAD);
        assertThat(road.name()).isEqualTo("NY 9A");
        assertThat(road.position()).isNull();
        assertThat(road.distanceMeters()).isCloseTo(111.2, within(1.0));
    }

    @Test
    void anElementOfSeveralKindsOnlyCountsAsThoseInRange() {
        // A pub that is also a hotel, 700 m away: in range for lodging (2 km), not for nightlife (250 m).
        stub(200, elements("""
                {"type":"node","id":4,"lat":40.7191,"lon":-74.0060,"tags":{"amenity":"pub","tourism":"hotel","name":"The Inn"}}"""));

        assertThat(client().around(ORIGIN).block()).extracting(Place::kind).containsExactly(PlaceKind.LODGING);
    }

    @Test
    void aLargeAreaOfOneKindCountsEvenIfItsCentreIsBeyondTheRadius() {
        // Overpass found the airport by its outline; its centre is 9 km away.
        stub(200, elements("""
                {"type":"way","id":5,"center":{"lat":40.7937,"lon":-74.0060},"tags":{"aeroway":"aerodrome","name":"Big Airport"}}"""));

        Place airport = client().around(ORIGIN).block().getFirst();

        assertThat(airport.kind()).isEqualTo(PlaceKind.AIRPORT);
        assertThat(airport.distanceMeters()).isGreaterThan(PlaceKind.AIRPORT.radiusMeters());
    }

    @Test
    void privatePlacesTunnelsUnknownTagsAndElementsWithoutAPositionAreSkipped() {
        stub(200, elements(
                """
                {"type":"way","id":6,"center":{"lat":40.7130,"lon":-74.0060},"tags":{"amenity":"parking","access":"private"}}""",
                """
                {"type":"way","id":7,"geometry":[{"lat":40.7130,"lon":-74.0070},{"lat":40.7130,"lon":-74.0050}],
                 "tags":{"railway":"subway","tunnel":"yes"}}""",
                """
                {"type":"node","id":8,"lat":40.7130,"lon":-74.0060,"tags":{"amenity":"bench"}}""",
                """
                {"type":"node","id":9,"lat":40.7130,"lon":-74.0060}""",
                """
                {"type":"relation","id":10,"tags":{"amenity":"hospital","name":"No Centre"}}"""));

        assertThat(client().around(ORIGIN).block()).isEmpty();
    }

    @Test
    void aGapInALineIsNotBridged() {
        // The two halves of the line lie far to the west and east; the missing middle passes right by.
        stub(200, elements("""
                {"type":"way","id":11,"geometry":[{"lat":40.7128,"lon":-74.0200},{"lat":40.7128,"lon":-74.0150},null,
                                                  {"lat":40.7128,"lon":-73.9970},{"lat":40.7128,"lon":-73.9920}],
                 "tags":{"railway":"rail"}}"""));

        assertThat(client().around(ORIGIN).block().getFirst().distanceMeters()).isGreaterThan(700);
    }

    @Test
    void theQueryIsPostedAsAFormWithTheUserAgent() {
        stub(200, elements());

        client().around(ORIGIN).block();

        api.verify(postRequestedFor(urlEqualTo(INTERPRETER))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("User-Agent", equalTo("CineScout-test"))
                .withRequestBody(containing("data=%5Bout%3Ajson%5D%5Btimeout%3A25%5D")));
    }

    @Test
    void aQueryOverpassCouldNotFinishIsUnavailableNotEmpty() {
        stub(200, """
                {"version":0.6,"elements":[],"remark":"runtime error: Query timed out in \\"query\\" at line 3 after 21 seconds."}""");

        Throwable error = catchThrowable(() -> client().around(ORIGIN).block());

        assertThat(error).isInstanceOf(LogisticsException.class).hasMessageContaining("could not finish");
        assertThat(((LogisticsException) error).kind()).isEqualTo(Kind.UNAVAILABLE);
    }

    @Test
    void tooManyRequestsIsRateLimitedAndABusyGatewayUnavailable() {
        stub(429, "rate_limited");
        assertThat(((LogisticsException) catchThrowable(() -> client().around(ORIGIN).block())).kind()).isEqualTo(Kind.RATE_LIMITED);

        stub(504, "<html>Gateway Timeout</html>");
        assertThat(((LogisticsException) catchThrowable(() -> client().around(ORIGIN).block())).kind()).isEqualTo(Kind.UNAVAILABLE);

        stub(400, "<html>parse error</html>");
        assertThat(((LogisticsException) catchThrowable(() -> client().around(ORIGIN).block())).kind()).isEqualTo(Kind.INVALID_REQUEST);
    }

    @Test
    void aQueryTurnedAwayAtOnceIsTriedOnceMoreAndNoFurther() {
        api.stubFor(post(urlEqualTo(INTERPRETER)).inScenario("busy").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(504)).willSetStateTo("free"));
        api.stubFor(post(urlEqualTo(INTERPRETER)).inScenario("busy").whenScenarioStateIs("free")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(elements())));

        assertThat(client().around(ORIGIN).block()).isEmpty();
        api.verify(2, postRequestedFor(urlEqualTo(INTERPRETER)));

        api.resetAll();
        stub(504, "<html>Gateway Timeout</html>");
        assertThat(catchThrowable(() -> client().around(ORIGIN).block())).isInstanceOf(LogisticsException.class);
        api.verify(2, postRequestedFor(urlEqualTo(INTERPRETER)));
    }

    @Test
    void aRequestTheServerRejectsAsWrongIsNotTriedAgain() {
        stub(400, "<html>parse error</html>");

        assertThat(catchThrowable(() -> client().around(ORIGIN).block())).isInstanceOf(LogisticsException.class);
        api.verify(1, postRequestedFor(urlEqualTo(INTERPRETER)));
    }

    // --- unit bases ---------------------------------------------------------------------------------

    @Test
    void aCarParksSizeComesFromItsCapacityAndItsBoundsAndItsCentreFromTheBounds() {
        // 0.002° north-south by 0.002° east-west at this latitude: about 222 m by 169 m.
        stub(200, elements("""
                {"type":"way","id":6,"bounds":{"minlat":40.7140,"minlon":-74.0070,"maxlat":40.7160,"maxlon":-74.0050},
                 "tags":{"amenity":"parking","parking":"surface","capacity":"120","name":"Pier Lot"}}"""));

        Place base = client().around(ORIGIN).block().stream().filter(place -> place.kind() == PlaceKind.UNIT_BASE).findFirst().orElseThrow();

        assertThat(base.position().latitude()).isCloseTo(40.7150, within(0.00001));
        assertThat(base.size().capacity()).isEqualTo(120);
        assertThat(base.size().areaSquareMeters()).isCloseTo(222.4 * 168.6, within(500.0));
        assertThat(base.size().type()).isEqualTo("surface");
        assertThat(base.name()).isEqualTo("Pier Lot");
    }

    @Test
    void underCoverPrivateOrInTheRoadIsNoUnitBaseButARestAreaIs() {
        stub(200, elements(
                """
                {"type":"node","id":7,"lat":40.7138,"lon":-74.0060,"tags":{"amenity":"parking","parking":"multi-storey"}}""",
                """
                {"type":"node","id":8,"lat":40.7138,"lon":-74.0061,"tags":{"amenity":"parking","access":"private"}}""",
                """
                {"type":"node","id":9,"lat":40.7138,"lon":-74.0062,"tags":{"amenity":"parking","parking":"lane"}}""",
                """
                {"type":"node","id":10,"lat":40.7138,"lon":-74.0063,"tags":{"highway":"rest_area","name":"Hudson Rest Area"}}""",
                """
                {"type":"node","id":11,"lat":40.7138,"lon":-74.0064,"tags":{"amenity":"parking","parking":"street_side","capacity":"lots"}}"""));

        List<Place> bases = client().around(ORIGIN).block().stream().filter(place -> place.kind() == PlaceKind.UNIT_BASE).toList();

        assertThat(bases).extracting(Place::name).containsExactly("Hudson Rest Area", null);
        assertThat(bases).extracting(place -> place.size().type()).containsExactly("rest_area", "street_side");
        assertThat(bases.getLast().size().capacity()).isNull();
    }
}
