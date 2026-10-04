package com.cinescout.web;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.routing.Route;
import com.cinescout.logistics.routing.RoutingClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Company moves end to end, with only the router replaced. */
@SpringBootTest(properties = {"cinescout.moves.max-lookups=2", "cinescout.moves.warn-after=45m"})
class MovesApiTest extends ApiTest {

    @MockitoBean RoutingClient router;

    @BeforeEach
    void routes() {
        jdbc.update("DELETE FROM route_cache"); // kept across tests otherwise, as it is across requests
        when(router.attribution()).thenReturn("Routing by OSRM, map data © OpenStreetMap contributors (ODbL)");
        // A minute a kilometre, measured as the crow flies: enough to tell the legs apart.
        when(router.drive(any(), any())).thenAnswer(call -> {
            GeoPoint from = call.getArgument(0);
            GeoPoint to = call.getArgument(1);
            double metres = from.distanceTo(to);
            return Mono.just(new Route(metres, metres * 0.06));
        });
    }

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "The Night Ferry"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String scene(Account owner, String project, int number, String day, String call) {
        String id = json(owner.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("sceneNumber", number, "title", "Scene " + number, "sourceText", "INT. SOMEWHERE - DAY"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        Map<String, Object> dates = new HashMap<>();
        dates.put("shootDateStart", day);
        dates.put("callTime", call);
        owner.client().put().uri("/api/scenes/" + id + "/shoot-dates").bodyValue(dates).exchange().expectStatus().isOk();
        return id;
    }

    private String venue(Account owner, String scene, String name, Double lat, Double lng) {
        Map<String, Object> body = new HashMap<>(Map.of("name", name));
        if (lat != null) {
            body.put("latitude", lat);
            body.put("longitude", lng);
        }
        String id = json(owner.client().post().uri("/api/scenes/" + scene + "/locations").bodyValue(body)
                .exchange().expectStatus().isCreated()).path("id").asText();
        owner.client().put().uri("/api/locations/" + id).bodyValue(Map.of("status", "CONFIRMED")).exchange().expectStatus().isOk();
        return id;
    }

    private JsonNode moves(Account who, String project) {
        return json(who.client().get().uri("/api/projects/" + project + "/moves").exchange().expectStatus().isOk());
    }

    @Test
    void aDaysVenuesAreVisitedByCallTimeAndEachPairIsAskedForOnce() {
        Account ada = register("Ada");
        String project = project(ada);
        // Script order 1, 2, 3; call times put scene 3 first.
        venue(ada, scene(ada, project, 1, "2026-11-02", "10:00"), "Camden Lock", 51.5413, -0.1464);
        venue(ada, scene(ada, project, 2, "2026-11-02", "14:00"), "Tower Bridge", 51.5055, -0.0754);
        venue(ada, scene(ada, project, 3, "2026-11-02", "07:00"), "Hampstead Heath", 51.5608, -0.1630);
        venue(ada, scene(ada, project, 4, "2026-11-03", "08:00"), "Alone that day", 51.5, -0.1);

        JsonNode response = moves(ada, project);

        assertThat(response.path("warnAfterMinutes").asInt()).isEqualTo(45);
        assertThat(response.path("attribution").asText()).contains("OSRM");
        assertThat(response.path("days").size()).isEqualTo(1);
        JsonNode day = response.path("days").get(0);
        assertThat(day.path("date").asText()).isEqualTo("2026-11-02");
        assertThat(day.path("moves").findValuesAsText("fromName")).containsExactly("Hampstead Heath", "Camden Lock");
        assertThat(day.path("moves").findValuesAsText("toName")).containsExactly("Camden Lock", "Tower Bridge");
        JsonNode first = day.path("moves").get(0);
        assertThat(first.path("status").asText()).isEqualTo("OK");
        assertThat(first.path("minutes").asInt()).isEqualTo((int) Math.ceil(new GeoPoint(51.5608, -0.1630).distanceTo(new GeoPoint(51.5413, -0.1464)) * 0.06 / 60));
        assertThat(first.path("text").asText()).matches("Hampstead Heath to Camden Lock: \\d+ min, \\d+\\.\\d km by road");
        verify(router, times(2)).drive(any(), any());

        clearInvocations(router);
        assertThat(moves(ada, project).path("days").get(0).path("moves").findValuesAsText("status")).containsOnly("OK");
        verify(router, never()).drive(any(), any());
    }

    @Test
    void aLongMoveIsFlaggedAndUnplacedOrRepeatedVenuesAreSaidPlainly() {
        Account ada = register("Ada");
        String project = project(ada);
        venue(ada, scene(ada, project, 1, "2026-11-02", "07:00"), "Brighton Pier", 50.8169, -0.1367);
        venue(ada, scene(ada, project, 2, "2026-11-02", "12:00"), "Camden Lock", 51.5413, -0.1464);
        venue(ada, scene(ada, project, 3, "2026-11-02", "15:00"), "The Camden Lock", 51.5413, -0.1464);
        venue(ada, scene(ada, project, 4, "2026-11-02", "18:00"), "Somewhere unplaced", null, null);

        JsonNode moves = moves(ada, project).path("days").get(0).path("moves");

        // Camden Lock twice in a row is no move.
        assertThat(moves.size()).isEqualTo(2);
        assertThat(moves.get(0).path("tooLong").asBoolean()).isTrue();
        assertThat(moves.get(0).path("text").asText()).contains(" h ");
        assertThat(moves.get(1).path("status").asText()).isEqualTo("UNPLACED");
        assertThat(moves.get(1).path("text").asText()).isEqualTo("The Camden Lock to Somewhere unplaced: set both venues' pins to work out the drive");
    }

    @Test
    void onlyAFewNewPairsAreLookedUpARequestAndAnOutageIsNotKept() {
        Account ada = register("Ada");
        String project = project(ada);
        for (int i = 0; i < 4; i++) {
            venue(ada, scene(ada, project, i + 1, "2026-11-02", "%02d:00".formatted(7 + i)), "Venue " + i, 51.5 + i * 0.01, -0.1);
        }
        doReturn(Mono.just(new Route(1000, 120)))
                .doReturn(Mono.error(new LogisticsException(LogisticsException.Kind.INVALID_REQUEST, "refused")))
                .when(router).drive(any(), any());

        JsonNode first = moves(ada, project).path("days").get(0).path("moves");
        assertThat(first.findValuesAsText("status")).containsExactly("OK", "UNAVAILABLE", "PENDING");

        doReturn(Mono.just(new Route(1000, 120))).when(router).drive(any(), any());
        assertThat(moves(ada, project).path("days").get(0).path("moves").findValuesAsText("status")).containsExactly("OK", "OK", "OK");
    }

    @Test
    void theSharedCallSheetShowsOnlyMovesAlreadyWorkedOutAndOutsidersSeeNothing() {
        Account ada = register("Ada");
        Account mallory = register("Mallory");
        String project = project(ada);
        venue(ada, scene(ada, project, 1, "2026-11-02", "07:00"), "Camden Lock", 51.5413, -0.1464);
        venue(ada, scene(ada, project, 2, "2026-11-02", "12:00"), "Tower Bridge", 51.5055, -0.0754);
        String token = json(ada.client().post().uri("/api/projects/" + project + "/call-sheet-link").exchange().expectStatus().is2xxSuccessful())
                .path("token").asText();

        JsonNode before = json(web.get().uri("/api/public/call-sheets/" + token).exchange().expectStatus().isOk());
        assertThat(before.path("moves").path("days").get(0).path("moves").get(0).path("status").asText()).isEqualTo("PENDING");
        verify(router, never()).drive(any(), any());

        moves(ada, project);
        JsonNode after = json(web.get().uri("/api/public/call-sheets/" + token).exchange().expectStatus().isOk());
        assertThat(after.path("moves").path("days").get(0).path("moves").get(0).path("status").asText()).isEqualTo("OK");

        mallory.client().get().uri("/api/projects/" + project + "/moves").exchange().expectStatus().isNotFound();
    }
}
