package com.cinescout.web;

import com.cinescout.domain.AdminArea;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.geocoding.Geocoder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The permit guide end to end with only the geocoder replaced; the filming offices are the repository's own file. */
class PermitApiTest extends ApiTest {

    private static final AdminArea CAMDEN = new AdminArea("London Borough of Camden", List.of("GB-CMD", "GB-ENG"), "gb", 0, 0);

    @MockitoBean Geocoder geocoder;

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String[] venue(Account owner, String start, Double lat, Double lng) {
        String project = json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "The Night Ferry"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        String scene = json(owner.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("sceneNumber", 4, "title", "Street", "sourceText", "EXT. HIGH STREET - DAY"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        if (start != null) {
            owner.client().put().uri("/api/scenes/" + scene + "/shoot-dates").bodyValue(Map.of("shootDateStart", start))
                    .exchange().expectStatus().isOk();
        }
        Map<String, Object> body = new HashMap<>(Map.of("name", "Camden High Street"));
        if (lat != null) {
            body.put("latitude", lat);
            body.put("longitude", lng);
        }
        String venue = json(owner.client().post().uri("/api/scenes/" + scene + "/locations").bodyValue(body)
                .exchange().expectStatus().isCreated()).path("id").asText();
        return new String[] {project, scene, venue};
    }

    private void markPublic(Account who, String venue) {
        who.client().put().uri("/api/locations/" + venue + "/booking-route").bodyValue(Map.of("bookingFriction", "PUBLIC"))
                .exchange().expectStatus().isOk();
    }

    private JsonNode permit(Account who, String venue) {
        return json(who.client().get().uri("/api/locations/" + venue + "/permit").exchange().expectStatus().isOk());
    }

    private static AdminArea at(AdminArea area, double lat, double lng) {
        return new AdminArea(area.name(), area.codes(), area.countryCode(), lat, lng);
    }

    @Test
    void aPublicSpaceInCamdenGetsCamdensOfficeLeadTimeAndChecklistLookedUpOnce() {
        when(geocoder.areaAt(any())).thenReturn(Mono.just(at(CAMDEN, 51.539, -0.1426)));
        Account ada = register("Ada");
        String venue = venue(ada, null, 51.539, -0.1426)[2];
        markPublic(ada, venue);

        JsonNode guide = permit(ada, venue);

        assertThat(guide.path("status").asText()).isEqualTo("FOUND");
        assertThat(guide.path("applies").asBoolean()).isTrue();
        assertThat(guide.path("areaName").asText()).isEqualTo("London Borough of Camden");
        JsonNode office = guide.path("office");
        assertThat(office.path("area").asText()).isEqualTo("Camden");
        assertThat(office.path("contactUrl").asText()).startsWith("https://");
        assertThat(office.path("leadTimeWorkingDays").asInt()).isEqualTo(5);
        assertThat(office.path("checklist").size()).isGreaterThan(3);
        assertThat(guide.path("lastReviewed").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(guide.path("editUrl").asText()).contains("filming-offices.yml");

        permit(ada, venue);
        verify(geocoder, times(1)).areaAt(any());
    }

    @Test
    void aMovedVenueIsLookedUpAgainAndABigCrewGetsTheLongerLeadTime() {
        when(geocoder.areaAt(any())).thenAnswer(call -> {
            GeoPoint point = call.getArgument(0);
            return Mono.just(at(CAMDEN, point.latitude(), point.longitude()));
        });
        Account ada = register("Ada");
        String[] ids = venue(ada, null, 51.539, -0.1426);
        jdbc.update("UPDATE scenes SET parse_status = 'PARSED', estimated_crew_size = 45 WHERE id = ?::uuid", ids[1]);
        permit(ada, ids[2]);

        ada.client().put().uri("/api/locations/" + ids[2] + "/coordinates").bodyValue(Map.of("latitude", 51.54, "longitude", -0.15))
                .exchange().expectStatus().isOk();

        assertThat(permit(ada, ids[2]).path("office").path("leadTimeWorkingDays").asInt()).isEqualTo(7);
        verify(geocoder, times(2)).areaAt(any());
    }

    @Test
    void elsewhereTheGuideSaysWhatItCanAndNeverFails() {
        Account ada = register("Ada");
        JsonNode unplaced = permit(ada, venue(ada, null, null, null)[2]);
        assertThat(unplaced.path("status").asText()).isEqualTo("NEEDS_POSITION");
        assertThat(unplaced.path("applies").asBoolean()).isFalse();

        when(geocoder.areaAt(any())).thenReturn(Mono.just(new AdminArea("Manchester", List.of("GB-MAN", "GB-ENG"), "gb", 53.48, -2.24)));
        JsonNode manchester = permit(ada, venue(ada, null, 53.48, -2.24)[2]);
        assertThat(manchester.path("status").asText()).isEqualTo("FALLBACK");
        assertThat(manchester.path("office").path("contactUrl").asText()).isEqualTo("https://www.gov.uk/find-local-council");
        assertThat(manchester.path("office").path("listed").asBoolean()).isFalse();

        when(geocoder.areaAt(any())).thenReturn(Mono.just(new AdminArea("Kings County", List.of("US-NY"), "us", 40.7, -73.99)));
        JsonNode brooklyn = permit(ada, venue(ada, null, 40.7, -73.99)[2]);
        assertThat(brooklyn.path("status").asText()).isEqualTo("OUTSIDE_COVERAGE");
        assertThat(brooklyn.path("office").isNull()).isTrue();

        when(geocoder.areaAt(any())).thenReturn(Mono.error(new LogisticsException(LogisticsException.Kind.UNAVAILABLE, "down")));
        JsonNode failed = permit(ada, venue(ada, null, 51.5, -0.12)[2]);
        assertThat(failed.path("status").asText()).isEqualTo("LOOKUP_FAILED");
        assertThat(failed.path("office").path("name").asText()).isEqualTo("The local council");
    }

    @Test
    void viewersReadTheGuideButOnlyEditorsSetTheBookingRouteAndOutsidersSeeNothing() {
        when(geocoder.areaAt(any())).thenReturn(Mono.just(at(CAMDEN, 51.539, -0.1426)));
        Account ada = register("Ada");
        Account vera = register("Vera");
        Account mallory = register("Mallory");
        String[] ids = venue(ada, null, 51.539, -0.1426);
        ada.client().post().uri("/api/projects/" + ids[0] + "/members").bodyValue(Map.of("email", vera.email(), "role", "VIEWER"))
                .exchange().expectStatus().is2xxSuccessful();

        assertThat(permit(vera, ids[2]).path("status").asText()).isEqualTo("FOUND");
        vera.client().put().uri("/api/locations/" + ids[2] + "/booking-route").bodyValue(Map.of("bookingFriction", "PUBLIC"))
                .exchange().expectStatus().isForbidden();
        mallory.client().get().uri("/api/locations/" + ids[2] + "/permit").exchange().expectStatus().isNotFound();
        mallory.client().put().uri("/api/locations/" + ids[2] + "/booking-route").bodyValue(Map.of("bookingFriction", "PUBLIC"))
                .exchange().expectStatus().isNotFound();

        JsonNode cleared = json(ada.client().put().uri("/api/locations/" + ids[2] + "/booking-route").bodyValue(new HashMap<String, Object>())
                .exchange().expectStatus().isOk());
        assertThat(cleared.path("bookingFriction").isNull()).isTrue();
        markPublic(ada, ids[2]);
        assertThat(json(ada.client().get().uri("/api/locations/" + ids[2]).exchange().expectStatus().isOk()).path("bookingFriction").asText())
                .isEqualTo("PUBLIC");
    }

    @Test
    void theScheduleSaysWhenToApplyForAConfirmedPublicSpaceAndWhenItIsTooLate() {
        when(geocoder.areaAt(any())).thenAnswer(call -> {
            GeoPoint point = call.getArgument(0);
            return Mono.just(at(CAMDEN, point.latitude(), point.longitude()));
        });
        Account ada = register("Ada");
        LocalDate farOff = LocalDate.now().plusWeeks(8).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        String[] ids = venue(ada, farOff.toString(), 51.539, -0.1426);
        markPublic(ada, ids[2]);
        ada.client().put().uri("/api/locations/" + ids[2]).bodyValue(Map.of("status", "CONFIRMED")).exchange().expectStatus().isOk();

        // Nothing until the venue's area is known.
        JsonNode before = json(ada.client().get().uri("/api/projects/" + ids[0] + "/schedule").exchange().expectStatus().isOk());
        assertThat(before.path("conflicts").size()).isZero();

        permit(ada, ids[2]);
        JsonNode conflict = json(ada.client().get().uri("/api/projects/" + ids[0] + "/schedule").exchange().expectStatus().isOk())
                .path("conflicts").get(0);
        assertThat(conflict.path("kind").asText()).isEqualTo("PERMIT_LEAD_TIME");
        assertThat(conflict.path("problem").asBoolean()).isFalse();
        assertThat(conflict.path("message").asText()).startsWith("Camden High Street is a public space in Camden: apply to Camden Film Office")
                .endsWith("(5 working days ahead).");

        ada.client().put().uri("/api/scenes/" + ids[1] + "/shoot-dates").bodyValue(Map.of("shootDateStart", LocalDate.now().plusDays(1).toString()))
                .exchange().expectStatus().isOk();
        JsonNode late = json(ada.client().get().uri("/api/projects/" + ids[0] + "/schedule").exchange().expectStatus().isOk())
                .path("conflicts").get(0);
        assertThat(late.path("problem").asBoolean()).isTrue();
        assertThat(late.path("message").asText()).contains("5 working days' notice", "Call Camden Film Office");
    }
}
