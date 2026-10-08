package com.cinescout.web;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import com.cinescout.logistics.geocoding.Geocoder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** "Use my current location": a position named by the geocoder, which is the only thing replaced. */
class PlaceApiTest extends ApiTest {

    @MockitoBean Geocoder geocoder;

    @Test
    void aPositionIsNamedWithTheMapsCredit() {
        when(geocoder.placeAt(new GeoPoint(51.526, -0.078), "en-GB,en;q=0.9")).thenReturn(Mono.just("Shoreditch, London, United Kingdom"));
        when(geocoder.attribution()).thenReturn("Geocoding by Nominatim");
        Account ada = register("Ada");

        JsonNode place = ada.client().get().uri("/api/places/here?lat=51.526&lng=-0.078").header("Accept-Language", "en-GB,en;q=0.9").exchange().expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(place.path("name").asText()).isEqualTo("Shoreditch, London, United Kingdom");
        assertThat(place.path("latitude").asDouble()).isEqualTo(51.526);
        assertThat(place.path("attribution").asText()).isEqualTo("Geocoding by Nominatim");
    }

    @Test
    void anUnknownSpotIs404TheMapBeingDownIs503AndNonsenseIs400() {
        Account ada = register("Ada");
        when(geocoder.placeAt(any(), any())).thenReturn(Mono.empty());
        ada.client().get().uri("/api/places/here?lat=0&lng=0").exchange().expectStatus().isNotFound();

        when(geocoder.placeAt(any(), any())).thenReturn(Mono.error(new LogisticsException(Kind.UNAVAILABLE, "down")));
        ada.client().get().uri("/api/places/here?lat=1&lng=1").exchange().expectStatus().isEqualTo(503);

        ada.client().get().uri("/api/places/here?lat=91&lng=0").exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("lat");
    }

    @Test
    void onlyForSignedInPeople() {
        web.get().uri("/api/places/here?lat=1&lng=1").exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(geocoder);
    }
}
