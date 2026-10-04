package com.cinescout.logistics.routing.osrm;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.routing.Route;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** The answer is a trimmed copy of a real one from the public OSRM server (Camden Lock to Tower Bridge, 2026-10-04). */
class OsrmRoutingClientTest {

    private static final String ROUTE = """
            {"code":"Ok","routes":[{"legs":[{"steps":[],"weight":1286.6,"summary":"","duration":1284.7,"distance":7790.7}],
              "weight_name":"routability","weight":1286.6,"duration":1284.7,"distance":7790.7}],
             "waypoints":[{"location":[-0.147152,51.541489],"name":"","distance":56.27},
                          {"location":[-0.074135,51.507956],"name":"Tower Bridge Approach","distance":123.54}]}""";

    private static final GeoPoint CAMDEN = new GeoPoint(51.5413, -0.1464);
    private static final GeoPoint TOWER_BRIDGE = new GeoPoint(51.5081, -0.0759);

    @RegisterExtension
    static WireMockExtension api = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private OsrmRoutingClient client() {
        Duration timeout = Duration.ofSeconds(5);
        return new OsrmRoutingClient(ProviderHttp.webClient(WebClient.builder(), api.baseUrl(), timeout, "CineScout-test"),
                new OsrmProperties(api.baseUrl(), timeout, Duration.ZERO, Duration.ZERO));
    }

    private void stub(int status, String body) {
        api.stubFor(get(urlPathMatching("/route/v1/driving/.*")).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    void theDriveIsReadAndAskedForLongitudeFirstWithoutTheRouteItself() {
        stub(200, ROUTE);

        assertThat(client().drive(CAMDEN, TOWER_BRIDGE).block()).isEqualTo(new Route(7790.7, 1284.7));
        api.verify(getRequestedFor(urlPathEqualTo("/route/v1/driving/-0.146400,51.541300;-0.075900,51.508100"))
                .withQueryParam("overview", equalTo("false")).withQueryParam("steps", equalTo("false")));
    }

    @Test
    void noRoadBetweenThemIsNoRoute() {
        stub(400, "{\"code\":\"NoRoute\",\"message\":\"Impossible route between points\"}");

        assertThat(client().drive(CAMDEN, TOWER_BRIDGE).block()).isNull();
    }

    @Test
    void aRefusedRequestAndAnOutageAreToldApart() {
        stub(400, "{\"code\":\"InvalidQuery\",\"message\":\"Query string malformed\"}");
        assertThat(catchThrowable(() -> client().drive(CAMDEN, TOWER_BRIDGE).block()))
                .isInstanceOfSatisfying(LogisticsException.class, e -> assertThat(e.kind()).isEqualTo(Kind.INVALID_REQUEST));

        stub(503, "<html>busy</html>");
        assertThat(catchThrowable(() -> client().drive(CAMDEN, TOWER_BRIDGE).block()))
                .isInstanceOfSatisfying(LogisticsException.class, e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));

        stub(429, "{}");
        assertThat(catchThrowable(() -> client().drive(CAMDEN, TOWER_BRIDGE).block()))
                .isInstanceOfSatisfying(LogisticsException.class, e -> assertThat(e.kind()).isEqualTo(Kind.RATE_LIMITED));
    }
}
