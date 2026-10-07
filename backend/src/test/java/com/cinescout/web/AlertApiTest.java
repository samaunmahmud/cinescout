package com.cinescout.web;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.weather.DailyWeather;
import com.cinescout.logistics.weather.WeatherClient;
import com.cinescout.logistics.weather.WeatherSeries;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alerts end to end: the weather watch and the follow-up job make them (run through their endpoints, as the scheduled
 * workflow does), each person lists, counts and reads their own. Only the weather service is mocked.
 */
@SpringBootTest(properties = "cinescout.jobs.secret=" + AlertApiTest.SECRET)
class AlertApiTest extends ApiTest {

    static final String SECRET = "test-alert-secret";
    private static final GeoPoint DINER = new GeoPoint(40.7128, -74.006);

    @MockitoBean WeatherClient weather;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void calmByDefault() {
        when(weather.attribution()).thenReturn("Weather by Test");
        doAnswer(call -> Mono.just(series(call.getArgument(1), call.getArgument(2), 10, 15.0))).when(weather).forecast(any(), any(), any());
    }

    private static WeatherSeries series(LocalDate from, LocalDate to, int rainChance, double wind) {
        return new WeatherSeries(ZoneId.of("America/New_York"), from.datesUntil(to.plusDays(1))
                .map(date -> new DailyWeather(date, 3, 18.0, 12.0, 0.0, rainChance, wind, wind + 15, 50)).toList());
    }

    // --- helpers ----------------------------------------------------------------------------------

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Neon Nights"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String scene(Account owner, String projectId, String title, LocalDate start, LocalDate end) {
        String id = json(owner.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("title", title, "sourceText", "EXT. ROOFTOP - DAY"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        Map<String, Object> dates = new HashMap<>();
        dates.put("shootDateStart", start == null ? null : start.toString());
        dates.put("shootDateEnd", end == null ? null : end.toString());
        owner.client().put().uri("/api/scenes/" + id + "/shoot-dates").bodyValue(dates).exchange().expectStatus().isOk();
        return id;
    }

    private String venue(Account owner, String sceneId, String name, boolean confirmed, boolean placed) {
        String id = json(owner.client().post().uri("/api/scenes/" + sceneId + "/locations").bodyValue(Map.of("name", name))
                .exchange().expectStatus().isCreated()).path("id").asText();
        if (placed) {
            owner.client().put().uri("/api/locations/" + id + "/coordinates")
                    .bodyValue(Map.of("latitude", DINER.latitude(), "longitude", DINER.longitude())).exchange().expectStatus().isOk();
        }
        if (confirmed) {
            owner.client().put().uri("/api/locations/" + id).bodyValue(Map.of("status", "CONFIRMED")).exchange().expectStatus().isOk();
        }
        return id;
    }

    private void addMember(Account owner, String projectId, Account who, String role) {
        owner.client().post().uri("/api/projects/" + projectId + "/members").bodyValue(Map.of("email", who.email(), "role", role))
                .exchange().expectStatus().is2xxSuccessful();
    }

    private JsonNode runJob(String name) {
        return json(web.post().uri("/api/internal/jobs/" + name).header("X-Job-Secret", SECRET).exchange().expectStatus().isOk());
    }

    private JsonNode alerts(Account who, String query) {
        return json(who.client().get().uri("/api/alerts" + query).exchange().expectStatus().isOk());
    }

    private long unread(Account who) {
        return json(who.client().get().uri("/api/alerts/unread-count").exchange().expectStatus().isOk()).path("unread").asLong();
    }

    // --- the weather watch ------------------------------------------------------------------------------

    @Test
    void rainOrWindOverTheThresholdsOnAShootDayAlertsTheWholeCrewOnceNamingTheCoverSets() {
        Account ada = register("Ada");
        Account vic = register("Vic");
        String project = project(ada);
        addMember(ada, project, vic, "VIEWER");
        String scene = scene(ada, project, "Rooftop chase", today.plusDays(1), today.plusDays(3));
        String rooftop = venue(ada, scene, "Skyline Rooftop", true, true);
        String warehouse = venue(ada, scene, "Dock Street Warehouse", false, false);
        ada.client().post().uri("/api/scenes/" + scene + "/covers").bodyValue(Map.of("locationId", warehouse, "trigger", "if rain > 60%"))
                .exchange().expectStatus().isCreated();
        // Rain likely on the first day, a gale on the second, a calm third.
        doReturn(Mono.just(new WeatherSeries(ZoneId.of("America/New_York"), List.of(
                new DailyWeather(today.plusDays(1), 61, 14.0, 9.0, 6.0, 75, 20.0, 35.0, 100),
                new DailyWeather(today.plusDays(2), 3, 16.0, 10.0, 0.0, 10, 45.0, 70.0, 40),
                new DailyWeather(today.plusDays(3), 0, 18.0, 11.0, 0.0, 5, 10.0, 20.0, 0))))).when(weather).forecast(any(), any(), any());

        JsonNode run = runJob("weather-watch");
        assertThat(run.path("job").asText()).isEqualTo("weather-watch");
        assertThat(run.path("affected").asInt()).isEqualTo(4); // two days, two people
        verify(weather).forecast(eq(DINER), eq(today.plusDays(1)), eq(today.plusDays(3)));

        JsonNode mine = alerts(ada, "");
        assertThat(mine.path("items")).hasSize(2);
        JsonNode rain = mine.path("items").findParents("payload").stream()
                .filter(alert -> alert.path("payload").path("day").asText().equals(today.plusDays(1).toString())).findFirst().orElseThrow();
        assertThat(rain.path("kind").asText()).isEqualTo("WEATHER");
        assertThat(rain.path("projectTitle").asText()).isEqualTo("Neon Nights");
        assertThat(rain.path("sceneId").asText()).isEqualTo(scene);
        assertThat(rain.path("locationId").asText()).isEqualTo(rooftop);
        assertThat(rain.path("read").asBoolean()).isFalse();
        JsonNode facts = rain.path("payload");
        assertThat(facts.path("venue").asText()).isEqualTo("Skyline Rooftop");
        assertThat(facts.path("reasons").get(0).asText()).isEqualTo("RAIN");
        assertThat(facts.path("rainChance").asInt()).isEqualTo(75);
        assertThat(facts.path("rainThreshold").asInt()).isEqualTo(60);
        assertThat(facts.path("covers").get(0).path("name").asText()).isEqualTo("Dock Street Warehouse");
        assertThat(facts.path("covers").get(0).path("trigger").asText()).isEqualTo("if rain > 60%");
        assertThat(alerts(vic, "").path("items")).hasSize(2);

        assertThat(runJob("weather-watch").path("affected").asInt()).isZero(); // once per venue and day
        assertThat(unread(ada)).isEqualTo(2);
    }

    @Test
    void onlyConfirmedPlacedVenuesShootingThisWeekAreWatchedAndTheProjectsThresholdsApply() {
        Account ada = register("Ada");
        String project = project(ada);
        venue(ada, scene(ada, project, "Next month", today.plusDays(30), null), "Far Off", true, true);
        venue(ada, scene(ada, project, "Unconfirmed", today.plusDays(1), null), "Maybe Place", false, true);
        venue(ada, scene(ada, project, "Unplaced", today.plusDays(1), null), "Nowhere Yet", true, false);
        venue(ada, scene(ada, project, "Undated", null, null), "Some Day", true, true);
        venue(ada, scene(ada, project, "Started yesterday", today.minusDays(1), today.plusDays(1)), "Ongoing", true, true);
        doAnswer(call -> Mono.just(series(call.getArgument(1), call.getArgument(2), 70, 15.0))).when(weather).forecast(any(), any(), any());

        ada.client().put().uri("/api/projects/" + project + "/settings").bodyValue(Map.of("followUpDays", 5, "rainAlertPercent", 80))
                .exchange().expectStatus().isOk();
        assertThat(runJob("weather-watch").path("affected").asInt()).isZero(); // 70% is under 80%
        verify(weather).forecast(any(), eq(today), eq(today.plusDays(1))); // only the ongoing scene, from today

        JsonNode settings = json(ada.client().put().uri("/api/projects/" + project + "/settings")
                .bodyValue(Map.of("followUpDays", 5, "windAlertKmh", 60)).exchange().expectStatus().isOk());
        assertThat(settings.path("rainAlertPercent").asInt()).isEqualTo(80); // left out, so kept
        assertThat(settings.path("windAlertKmh").asInt()).isEqualTo(60);
        ada.client().put().uri("/api/projects/" + project + "/settings").bodyValue(Map.of("followUpDays", 5, "rainAlertPercent", 0))
                .exchange().expectStatus().isBadRequest();
        ada.client().put().uri("/api/projects/" + project + "/settings").bodyValue(Map.of("followUpDays", 5, "rainAlertPercent", 50))
                .exchange().expectStatus().isOk();
        assertThat(runJob("weather-watch").path("affected").asInt()).isEqualTo(2); // today and tomorrow
    }

    @Test
    void aVenueTheWeatherServiceFailsOnIsSkippedAndTheRestStillWatched() {
        Account ada = register("Ada");
        String project = project(ada);
        venue(ada, scene(ada, project, "First", today, null), "Broken", true, true);
        venue(ada, scene(ada, project, "Second", today.plusDays(1), null), "Fine", true, true);
        doReturn(Mono.error(new LogisticsException(LogisticsException.Kind.INVALID_REQUEST, "bad"))).when(weather).forecast(any(), eq(today), any());
        doAnswer(call -> Mono.just(series(call.getArgument(1), call.getArgument(2), 90, 10.0))).when(weather).forecast(any(), eq(today.plusDays(1)), any());

        assertThat(runJob("weather-watch").path("affected").asInt()).isEqualTo(1);
        assertThat(alerts(ada, "").path("items").get(0).path("payload").path("venue").asText()).isEqualTo("Fine");
    }

    // --- reading them -----------------------------------------------------------------------------------

    @Test
    void eachPersonReadsOnlyTheirOwnAndLosesThemWhenTheyLeaveTheProject() {
        Account ada = register("Ada");
        Account eve = register("Eve");
        String project = project(ada);
        addMember(ada, project, eve, "EDITOR");
        venue(ada, scene(ada, project, "Storm", today, today.plusDays(1)), "Skyline Rooftop", true, true);
        doAnswer(call -> Mono.just(series(call.getArgument(1), call.getArgument(2), 90, 10.0))).when(weather).forecast(any(), any(), any());
        runJob("weather-watch");

        JsonNode mine = alerts(ada, "?unread=true");
        assertThat(mine.path("totalItems").asInt()).isEqualTo(2);
        String first = mine.path("items").get(0).path("id").asText();
        ada.client().post().uri("/api/alerts/" + first + "/read").exchange().expectStatus().isNoContent();
        ada.client().post().uri("/api/alerts/" + first + "/read").exchange().expectStatus().isNoContent(); // again is fine
        assertThat(unread(ada)).isEqualTo(1);
        assertThat(alerts(ada, "?unread=true").path("items").findValuesAsText("id")).doesNotContain(first);
        assertThat(alerts(ada, "").path("items").get(0).has("read")).isTrue();

        eve.client().post().uri("/api/alerts/" + first + "/read").exchange().expectStatus().isNotFound(); // Ada's
        ada.client().post().uri("/api/alerts/read-all").exchange().expectStatus().isNoContent();
        assertThat(unread(ada)).isZero();
        assertThat(unread(eve)).isEqualTo(2);

        ada.client().delete().uri("/api/projects/" + project + "/members/" + eve.id()).exchange().expectStatus().isNoContent();
        assertThat(unread(eve)).isZero();
        assertThat(alerts(eve, "").path("items")).isEmpty();

        web.get().uri("/api/alerts").exchange().expectStatus().isUnauthorized();
    }

    // --- follow-ups -------------------------------------------------------------------------------------

    @Test
    void anEmailFlaggedForAFollowUpAlertsTheOwnerAndEditorsOnce() {
        Account ada = register("Ada");
        Account eve = register("Eve");
        Account vic = register("Vic");
        String project = project(ada);
        addMember(ada, project, eve, "EDITOR");
        addMember(ada, project, vic, "VIEWER");
        String location = venue(ada, scene(ada, project, "Rooftop", null, null), "The Sky Bar", false, false);
        String draft = jdbc.queryForObject("""
                INSERT INTO outreach_drafts (location_id, created_by, recipient_name, recipient_email, subject, body, status, sent_at, reply_token)
                SELECT ?::uuid, p.owner_id, 'Tom', 'tom@skybar.example', 'Location enquiry: Neon Nights', 'BODY', 'SENT',
                       now() - make_interval(days => 9), md5(random()::text)
                FROM locations l JOIN scenes s ON s.id = l.scene_id JOIN projects p ON p.id = s.project_id WHERE l.id = ?::uuid
                RETURNING id""", String.class, location, location);

        runJob("follow-ups");
        runJob("follow-ups");

        JsonNode alert = alerts(ada, "").path("items").get(0);
        assertThat(alerts(ada, "").path("items")).hasSize(1);
        assertThat(alert.path("kind").asText()).isEqualTo("FOLLOW_UP");
        assertThat(alert.path("draftId").asText()).isEqualTo(draft);
        assertThat(alert.path("locationId").asText()).isEqualTo(location);
        assertThat(alert.path("payload").path("venue").asText()).isEqualTo("The Sky Bar");
        assertThat(alert.path("payload").path("subject").asText()).isEqualTo("Location enquiry: Neon Nights");
        assertThat(alert.path("payload").has("body")).isFalse();
        assertThat(unread(eve)).isEqualTo(1);
        assertThat(unread(vic)).isZero();
        verify(weather, never()).forecast(any(), any(), any());
    }
}
