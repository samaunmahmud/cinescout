package com.cinescout.web;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.LogisticsException.Kind;
import com.cinescout.logistics.geocoding.Geocoder;
import com.cinescout.logistics.places.Place;
import com.cinescout.logistics.places.PlaceKind;
import com.cinescout.logistics.places.PlacesClient;
import com.cinescout.logistics.weather.DailyWeather;
import com.cinescout.logistics.weather.WeatherClient;
import com.cinescout.logistics.weather.WeatherSeries;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The logistics endpoints end to end (HTTP, security, the real service, PostgreSQL) with only the three
 * providers (weather, places, geocoding) replaced by mocks. No AI keys are set: logistics must not need them.
 */
@SpringBootTest(properties = {
        // Fast retries, and breakers that cannot open mid-suite and disturb later tests.
        "cinescout.resilience.initial-backoff=1ms", "cinescout.resilience.max-backoff=5ms",
        "cinescout.resilience.breaker-window-size=100", "cinescout.resilience.breaker-minimum-calls=100"
})
class LogisticsApiTest extends ApiTest {

    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final GeoPoint BROOKLYN = new GeoPoint(40.6982888, -73.9999297);

    @MockitoBean WeatherClient weather;
    @MockitoBean PlacesClient places;
    @MockitoBean Geocoder geocoder;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void defaultAnswers() {
        when(weather.forecastDaysAhead()).thenReturn(15);
        when(weather.forecastDaysBack()).thenReturn(30);
        when(weather.attribution()).thenReturn("Weather by Test");
        when(places.attribution()).thenReturn("Map by Test");
        when(geocoder.attribution()).thenReturn("Geocoding by Test");
        when(weather.forecast(any(), any(), any())).thenAnswer(call -> Mono.just(series(call.getArgument(1), call.getArgument(2))));
        when(weather.history(any(), any(), any())).thenAnswer(call -> Mono.just(series(call.getArgument(1), call.getArgument(2))));
        when(places.around(any())).thenReturn(Mono.just(List.of(
                new Place(PlaceKind.RAILWAY, "Main Line", null, 100),
                new Place(PlaceKind.NIGHTLIFE, "Sky Bar", new GeoPoint(40.7128, -74.006), 0), // the venue itself
                new Place(PlaceKind.HOSPITAL, "General Hospital", new GeoPoint(40.72, -74.0), 2_000))));
        when(geocoder.locate(any())).thenReturn(Mono.just(BROOKLYN));
    }

    /** Blustery, overcast days in New York. */
    private static WeatherSeries series(LocalDate from, LocalDate to) {
        return new WeatherSeries(NEW_YORK, from.datesUntil(to.plusDays(1))
                .map(date -> new DailyWeather(date, 3, 18.0, 12.0, 0.0, 10, 25.0, 55.0, 90))
                .toList());
    }

    // --- helpers ----------------------------------------------------------------------------------

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects")
                .bodyValue(Map.of("title", "Neon Nights", "locationArea", "Brooklyn, New York"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    /** A scene shot on the given days from today; no dates at all when {@code days} is empty. */
    private String scene(Account owner, String projectId, int... days) {
        Map<String, Object> body = new HashMap<>(Map.of("sceneNumber", 1, "title", "Rooftop", "sourceText", "EXT. ROOFTOP - DUSK"));
        if (days.length > 0) {
            body.put("shootDateStart", today.plusDays(days[0]).toString());
            body.put("shootDateEnd", today.plusDays(days[days.length - 1]).toString());
        }
        String id = json(owner.client().post().uri("/api/projects/" + projectId + "/scenes").bodyValue(body)
                .exchange().expectStatus().isCreated()).path("id").asText();
        jdbc.update("""
                UPDATE scenes SET parse_status = 'PARSED', setting_type = 'rooftop bar', time_of_day = 'dusk',
                       acoustic_sensitivity = 'HIGH' WHERE id = ?::uuid""", id);
        return id;
    }

    private String location(Account owner, String sceneId, Map<String, Object> fields) {
        Map<String, Object> body = new HashMap<>(Map.of("name", "The Sky Bar"));
        body.putAll(fields);
        return json(owner.client().post().uri("/api/scenes/" + sceneId + "/locations").bodyValue(body)
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    /** A located venue for a scene shot from tomorrow for three days. */
    private String locatedVenue(Account owner) {
        return location(owner, scene(owner, project(owner), 1, 2, 3),
                Map.of("latitude", new BigDecimal("40.712800"), "longitude", new BigDecimal("-74.006000")));
    }

    private ResponseSpec refresh(Account owner, String locationId) {
        return owner.client().post().uri("/api/locations/" + locationId + "/logistics").exchange();
    }

    private JsonNode refreshed(Account owner, String locationId) {
        return json(refresh(owner, locationId).expectStatus().isOk());
    }

    private static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    // --- the report ----------------------------------------------------------------------------------

    @Test
    void theReportCoversEveryShootDayInTheLocationsTimeZone() {
        Account ada = register("Ada");

        JsonNode report = refreshed(ada, locatedVenue(ada));

        assertThat(report.path("version").asInt()).isEqualTo(1);
        assertThat(report.path("timeZone").asText()).isEqualTo("America/New_York");
        assertThat(report.path("position").path("geocoded").asBoolean()).isFalse();
        assertThat(report.path("position").path("latitude").decimalValue()).isEqualByComparingTo("40.7128");
        assertThat(report.path("shootWindow").path("start").asText()).isEqualTo(today.plusDays(1).toString());
        assertThat(report.path("shootWindow").path("end").asText()).isEqualTo(today.plusDays(3).toString());
        assertThat(report.path("shootWindow").path("assumed").asBoolean()).isFalse();
        assertThat(report.path("notes")).isEmpty();
        assertThat(texts(report.path("attribution"))).containsExactly("Weather by Test", "Map by Test");
        verifyNoInteractions(geocoder);
    }

    @Test
    void theSolarSectionShowsTheScenesOwnLightInLocalTime() {
        Account ada = register("Ada");

        JsonNode solar = refreshed(ada, locatedVenue(ada)).path("solar");

        assertThat(solar.path("timeOfDay").asText()).isEqualTo("dusk");
        assertThat(solar.path("sceneLight").asText()).isEqualTo("DUSK");
        assertThat(solar.path("days")).hasSize(3);
        JsonNode first = solar.path("days").get(0);
        assertThat(first.path("date").asText()).isEqualTo(today.plusDays(1).toString());
        OffsetDateTime sunset = OffsetDateTime.parse(first.path("sunset").asText());
        assertThat(sunset.getOffset()).isEqualTo(NEW_YORK.getRules().getOffset(sunset.toInstant())); // local, not UTC
        assertThat(sunset.getHour()).isBetween(16, 21);
        assertThat(first.path("sceneWindows")).hasSize(1);
        OffsetDateTime duskStart = OffsetDateTime.parse(first.path("sceneWindows").get(0).path("start").asText());
        assertThat(duskStart).isBefore(sunset);
        assertThat(first.path("goldenHours")).hasSize(2);
        assertThat(first.path("blueHours")).hasSize(2);
    }

    @Test
    void theWeatherIsForecastWithWarningsForTheScene() {
        Account ada = register("Ada");

        JsonNode weatherSection = refreshed(ada, locatedVenue(ada)).path("weather");

        assertThat(weatherSection.path("status").asText()).isEqualTo("OK");
        assertThat(weatherSection.path("days")).hasSize(3);
        JsonNode day = weatherSection.path("days").get(0);
        assertThat(day.path("basis").asText()).isEqualTo("FORECAST");
        assertThat(day.path("summary").asText()).isEqualTo("Overcast");
        assertThat(day.path("windGustsMaxKmh").asDouble()).isEqualTo(55.0);
        assertThat(texts(day.path("warnings"))).containsExactly(
                "Strong gusts: secure flags, frames and overheads; drones may be grounded",
                "Mostly overcast: little direct sun at golden hour");
        verify(weather).forecast(eq(new GeoPoint(40.7128, -74.006)), eq(today.plusDays(1)), eq(today.plusDays(3)));
        verify(weather, never()).history(any(), any(), any());
    }

    @Test
    void theEnvironmentWeighsNoiseAgainstTheScenesSensitivity() {
        Account ada = register("Ada");

        JsonNode environment = refreshed(ada, locatedVenue(ada)).path("environment");

        assertThat(environment.path("status").asText()).isEqualTo("OK");
        assertThat(environment.path("acousticSensitivity").asText()).isEqualTo("HIGH");
        assertThat(environment.path("noiseRisk").asText()).isEqualTo("HIGH");
        assertThat(environment.path("noiseSources")).hasSize(1); // not the Sky Bar, which is the venue
        assertThat(environment.path("noiseSources").get(0).path("name").asText()).isEqualTo("Main Line");
        assertThat(environment.path("noiseSources").get(0).path("advice").asText()).contains("timetable");
        assertThat(environment.path("nearbyServices").get(0).path("kind").asText()).isEqualTo("HOSPITAL");
        assertThat(environment.path("nearbyServices").get(0).path("distanceMeters").asInt()).isEqualTo(2_000);
    }

    @Test
    void aShootMonthsAwayGetsLastYearsWeatherLabelledAsSuch() {
        Account ada = register("Ada");
        String location = location(ada, scene(ada, project(ada), 100),
                Map.of("latitude", new BigDecimal("40.7128"), "longitude", new BigDecimal("-74.006")));

        JsonNode report = refreshed(ada, location);

        JsonNode day = report.path("weather").path("days").get(0);
        assertThat(day.path("basis").asText()).isEqualTo("PAST_YEAR");
        assertThat(day.path("referenceDate").asText()).isEqualTo(today.plusDays(100).minusYears(1).toString());
        assertThat(texts(report.path("notes"))).anyMatch(note -> note.contains("same date in an earlier year"));
        verify(weather, never()).forecast(any(), any(), any());
    }

    @Test
    void aSceneWithoutDatesIsShownTheComingWeek() {
        Account ada = register("Ada");
        String location = location(ada, scene(ada, project(ada)),
                Map.of("latitude", new BigDecimal("40.7128"), "longitude", new BigDecimal("-74.006")));

        JsonNode report = refreshed(ada, location);

        assertThat(report.path("shootWindow").path("assumed").asBoolean()).isTrue();
        assertThat(report.path("solar").path("days")).hasSize(7);
        assertThat(texts(report.path("notes"))).contains("The scene has no shoot dates, so the coming week is shown.");
    }

    // --- caching -----------------------------------------------------------------------------------------

    @Test
    void theReportIsCachedOnTheLocationExactlyAsReturned() {
        Account ada = register("Ada");
        String location = locatedVenue(ada);
        ada.client().get().uri("/api/locations/" + location + "/logistics").exchange()
                .expectStatus().isNotFound().expectHeader().contentType(PROBLEM);

        JsonNode report = refreshed(ada, location);

        JsonNode cached = json(ada.client().get().uri("/api/locations/" + location + "/logistics").exchange().expectStatus().isOk());
        assertThat(cached).isEqualTo(report); // local offsets survive the round trip through the database
        JsonNode venue = json(ada.client().get().uri("/api/locations/" + location).exchange().expectStatus().isOk());
        assertThat(venue.path("logistics")).isEqualTo(report);
        assertThat(venue.path("logisticsFetchedAt").isNull()).isFalse();
    }

    @Test
    void movingALocationDropsItsCachedLogistics() {
        Account ada = register("Ada");
        String location = locatedVenue(ada);
        refreshed(ada, location);

        JsonNode moved = json(ada.client().put().uri("/api/locations/" + location + "/coordinates")
                .bodyValue(Map.of("latitude", 40.7, "longitude", -73.99)).exchange().expectStatus().isOk());

        assertThat(moved.path("latitude").decimalValue()).isEqualByComparingTo("40.7");
        assertThat(moved.path("logistics").isNull()).isTrue();
        assertThat(moved.path("logisticsFetchedAt").isNull()).isTrue();
        ada.client().get().uri("/api/locations/" + location + "/logistics").exchange().expectStatus().isNotFound();
        refreshed(ada, location);
        verify(places).around(new GeoPoint(40.7, -73.99));
    }

    @Test
    void coordinatesAreValidated() {
        Account ada = register("Ada");
        String location = locatedVenue(ada);

        for (Map<String, Object> bad : List.<Map<String, Object>>of(
                Map.of("latitude", 40.7),
                Map.of("latitude", 91, "longitude", 0),
                Map.of("latitude", 40.1234567, "longitude", 0))) {
            ada.client().put().uri("/api/locations/" + location + "/coordinates").bodyValue(bad).exchange()
                    .expectStatus().isBadRequest().expectHeader().contentType(PROBLEM);
        }
    }

    // --- geocoding -----------------------------------------------------------------------------------------

    @Test
    void aVenueWithoutCoordinatesIsLookedUpByItsAddressAndTheResultKept() {
        Account ada = register("Ada");
        String location = location(ada, scene(ada, project(ada), 1), Map.of("address", "334 Furman St, Brooklyn"));

        JsonNode report = refreshed(ada, location);

        verify(geocoder).locate("334 Furman St, Brooklyn");
        assertThat(report.path("position").path("geocoded").asBoolean()).isTrue();
        assertThat(report.path("position").path("latitude").decimalValue()).isEqualByComparingTo("40.698289");
        assertThat(texts(report.path("notes"))).anyMatch(note -> note.contains("looked up from the venue's address"));
        assertThat(texts(report.path("attribution"))).contains("Geocoding by Test");
        JsonNode venue = json(ada.client().get().uri("/api/locations/" + location).exchange().expectStatus().isOk());
        assertThat(venue.path("latitude").decimalValue()).isEqualByComparingTo("40.698289");
        assertThat(venue.path("longitude").decimalValue()).isEqualByComparingTo("-73.999930");
        assertThat(venue.path("logistics")).isEqualTo(report);

        refreshed(ada, location);
        verify(geocoder).locate(any()); // still only the once: the coordinates were kept
    }

    @Test
    void aVenueWithoutAnAddressIsLookedUpByNameInTheProjectsArea() {
        Account ada = register("Ada");
        String location = location(ada, scene(ada, project(ada), 1), Map.of());

        JsonNode report = refreshed(ada, location);

        verify(geocoder).locate("The Sky Bar, Brooklyn, New York");
        assertThat(texts(report.path("notes"))).anyMatch(note -> note.contains("looked up from the venue's name"));
    }

    @Test
    void aVenueThatCannotBeFoundOnTheMapIsAConflict() {
        when(geocoder.locate(any())).thenReturn(Mono.empty());
        Account ada = register("Ada");
        String location = location(ada, scene(ada, project(ada), 1), Map.of());

        refresh(ada, location).expectStatus().isEqualTo(409).expectHeader().contentType(PROBLEM)
                .expectBody().jsonPath("$.detail").value(detail -> assertThat((String) detail).contains("set its coordinates"));
        verifyNoInteractions(places);
        assertThat(jdbc.queryForObject("SELECT logistics_json IS NULL AND latitude IS NULL FROM locations WHERE id = ?::uuid",
                Boolean.class, location)).isTrue();
    }

    @Test
    void aGeocoderOutageIsServiceUnavailableAndSaysNothingAboutTheVenue() {
        when(geocoder.locate(any())).thenReturn(Mono.error(new LogisticsException(Kind.UNAVAILABLE, "Nominatim returned HTTP 503")));
        Account ada = register("Ada");
        String location = location(ada, scene(ada, project(ada), 1), Map.of("address", "SECRET-ADDRESS 1 Roof St"));

        refresh(ada, location).expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "30")
                .expectBody()
                .jsonPath("$.retryable").isEqualTo(true)
                .jsonPath("$.detail").isEqualTo("The map service is unavailable; try again shortly");
    }

    // --- a provider down does not sink the report ----------------------------------------------------------

    @Test
    void withTheWeatherServiceDownTheRestOfTheReportStillComesBackInUtc() {
        doReturn(Mono.error(new LogisticsException(Kind.UNAVAILABLE, "down"))).when(weather).forecast(any(), any(), any());
        Account ada = register("Ada");

        JsonNode report = refreshed(ada, locatedVenue(ada));

        assertThat(report.path("weather").path("status").asText()).isEqualTo("UNAVAILABLE");
        assertThat(report.path("weather").path("message").asText()).isEqualTo("The weather service could not be reached; try again later");
        assertThat(report.path("weather").path("days")).isEmpty();
        assertThat(report.path("timeZone").asText()).isEqualTo("UTC");
        assertThat(texts(report.path("notes"))).contains("Times are in UTC: the location's time zone could not be looked up.");
        assertThat(report.path("solar").path("days")).hasSize(3);
        assertThat(report.path("environment").path("status").asText()).isEqualTo("OK");
        assertThat(texts(report.path("attribution"))).containsExactly("Map by Test");
    }

    @Test
    void withTheMapServiceDownTheRestOfTheReportStillComesBack() {
        when(places.around(any())).thenReturn(Mono.error(new LogisticsException(Kind.RATE_LIMITED, "Overpass returned HTTP 429")));
        Account ada = register("Ada");

        JsonNode report = refreshed(ada, locatedVenue(ada));

        JsonNode environment = report.path("environment");
        assertThat(environment.path("status").asText()).isEqualTo("UNAVAILABLE");
        assertThat(environment.path("noiseRisk").isNull()).isTrue();
        assertThat(environment.path("noiseSources")).isEmpty();
        assertThat(report.path("weather").path("status").asText()).isEqualTo("OK");
        verify(places).around(any()); // tried once: retrying a busy map server only adds to its load
    }

    @Test
    void aWindowAcrossTheForecastHorizonIsPartialIfOneSourceFails() {
        doReturn(Mono.error(new LogisticsException(Kind.UNAVAILABLE, "down"))).when(weather).history(any(), any(), any());
        Account ada = register("Ada");
        String location = location(ada, scene(ada, project(ada), 14, 15, 16, 17),
                Map.of("latitude", new BigDecimal("40.7128"), "longitude", new BigDecimal("-74.006")));

        JsonNode weatherSection = refreshed(ada, location).path("weather");

        assertThat(weatherSection.path("status").asText()).isEqualTo("PARTIAL");
        assertThat(weatherSection.path("message").asText()).isEqualTo("Weather for 2 of 4 days could not be looked up");
        assertThat(weatherSection.path("days")).hasSize(2);
        verify(weather, times(3)).history(any(), any(), any()); // the weather service is retried
    }

    // --- ownership and authentication ----------------------------------------------------------------------

    @Test
    void anotherUsersLocationIsNotFoundAndNoProviderIsCalled() {
        Account ada = register("Ada");
        Account eve = register("Eve");
        String location = locatedVenue(ada);
        refreshed(ada, location);
        clearInvocations(weather, places, geocoder);

        refresh(eve, location).expectStatus().isNotFound();
        eve.client().get().uri("/api/locations/" + location + "/logistics").exchange().expectStatus().isNotFound();
        eve.client().put().uri("/api/locations/" + location + "/coordinates")
                .bodyValue(Map.of("latitude", 1, "longitude", 1)).exchange().expectStatus().isNotFound();

        verify(weather, never()).forecast(any(), any(), any());
        verify(places, never()).around(any());
    }

    @Test
    void logisticsNeedAnAccount() {
        web.post().uri("/api/locations/" + UUID.randomUUID() + "/logistics").exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/locations/" + UUID.randomUUID() + "/logistics").exchange().expectStatus().isUnauthorized();
    }

    // --- a project's confirmed venues -------------------------------------------------------------

    private void confirm(Account owner, String locationId) {
        owner.client().put().uri("/api/locations/" + locationId).bodyValue(Map.of("status", "CONFIRMED")).exchange().expectStatus().isOk();
    }

    @Test
    void aProjectsConfirmedVenuesWithoutLogisticsAreWorkedOutAndTheOthersLeftAlone() {
        Account ada = register("Ada");
        String project = project(ada);
        String scene = scene(ada, project, 1, 2);
        Map<String, Object> located = Map.of("latitude", new BigDecimal("40.712800"), "longitude", new BigDecimal("-74.006000"));
        String confirmed = location(ada, scene, located);
        String alreadyDone = location(ada, scene, Map.of("name", "Done", "latitude", new BigDecimal("40.7"), "longitude", new BigDecimal("-74.0")));
        String lost = location(ada, scene, Map.of("name", "Nowhere"));
        String suggested = location(ada, scene, Map.of("name", "Maybe", "latitude", new BigDecimal("40.7"), "longitude", new BigDecimal("-74.0")));
        confirm(ada, confirmed);
        confirm(ada, alreadyDone);
        confirm(ada, lost);
        String covering = "{\"solar\":{\"days\":[{\"date\":\"" + today.plusDays(1) + "\"}]}}";
        jdbc.update("UPDATE locations SET logistics_json = ?::jsonb WHERE id = ?::uuid", covering, alreadyDone);
        when(geocoder.locate(any())).thenReturn(Mono.empty());

        JsonNode result = json(ada.client().post().uri("/api/projects/" + project + "/logistics").exchange().expectStatus().isOk());

        assertThat(result.path("updated").asInt()).isEqualTo(1);
        assertThat(result.path("failed").asInt()).isEqualTo(1);
        assertThat(result.path("remaining").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT logistics_json IS NOT NULL FROM locations WHERE id = ?::uuid", Boolean.class, confirmed)).isTrue();
        assertThat(jdbc.queryForObject("SELECT logistics_json->'solar'->'days'->0->>'date' FROM locations WHERE id = ?::uuid", String.class, alreadyDone))
                .isEqualTo(today.plusDays(1).toString());
        assertThat(jdbc.queryForObject("SELECT logistics_json IS NULL FROM locations WHERE id = ?::uuid", Boolean.class, suggested)).isTrue();
        assertThat(lost).isNotBlank();
    }

    @Test
    void aProjectWithNothingToDoSaysSoAndAnotherUsersIsA404() {
        Account ada = register("Ada");
        String project = project(ada);

        ada.client().post().uri("/api/projects/" + project + "/logistics").exchange().expectStatus().isOk()
                .expectBody().json("{\"updated\":0,\"failed\":0,\"remaining\":0}");
        register("Grace").client().post().uri("/api/projects/" + project + "/logistics").exchange().expectStatus().isNotFound();
    }

    @Test
    void aReportFromBeforeTheSceneWasMovedIsWorkedOutAgain() {
        Account ada = register("Ada");
        String project = project(ada);
        String venue = location(ada, scene(ada, project, 5), Map.of("latitude", new BigDecimal("40.712800"), "longitude", new BigDecimal("-74.006000")));
        confirm(ada, venue);
        String stale = "{\"solar\":{\"days\":[{\"date\":\"" + today.plusDays(1) + "\"}]}}";
        jdbc.update("UPDATE locations SET logistics_json = ?::jsonb WHERE id = ?::uuid", stale, venue);

        ada.client().post().uri("/api/projects/" + project + "/logistics").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.updated").isEqualTo(1).jsonPath("$.remaining").isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT logistics_json->'solar'->'days'->0->>'date' FROM locations WHERE id = ?::uuid", String.class, venue))
                .isEqualTo(today.plusDays(5).toString());
    }
}
