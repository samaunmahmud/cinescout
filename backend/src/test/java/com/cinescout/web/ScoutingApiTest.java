package com.cinescout.web;

import com.cinescout.ai.SearchResult;
import com.cinescout.ai.VenueVerdict;
import com.cinescout.ai.VenueVerdict.SettingMatch;
import com.cinescout.ai.Verdicts;
import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.llm.LlmClient;
import com.cinescout.llm.LlmException;
import com.cinescout.llm.LlmException.Kind;
import com.cinescout.search.LocationSearchClient;
import com.cinescout.search.LocationSearchRequest;
import com.cinescout.search.SearchException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The scouting endpoints end to end (HTTP, security, the real pipeline and service, PostgreSQL) with
 * only the two external providers replaced by mocks.
 */
@SpringBootTest(properties = {
        "cinescout.llm.watsonx.api-key=dummy", "cinescout.llm.watsonx.project-id=dummy", "cinescout.llm.watsonx.model-id=dummy",
        "cinescout.search.parallel.api-key=dummy",
        // Fast retries, and a breaker that cannot open mid-suite and disturb later tests.
        "cinescout.resilience.initial-backoff=1ms", "cinescout.resilience.max-backoff=5ms",
        "cinescout.resilience.breaker-window-size=100", "cinescout.resilience.breaker-minimum-calls=100"
})
class ScoutingApiTest extends ApiTest {

    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;
    private static final SceneRequirements REQUIREMENTS =
            new SceneRequirements("rooftop bar", "neon noir", null, "night", AcousticSensitivity.HIGH, 12);

    @MockitoBean LlmClient llm;
    @MockitoBean LocationSearchClient search;

    @BeforeEach
    void defaultAnswers() {
        when(llm.generate(any(), any(), eq(SceneRequirements.class))).thenReturn(Mono.just(REQUIREMENTS));
        when(llm.generate(any(), any(), eq(VenueVerdict.class))).thenAnswer(call -> {
            String prompt = call.getArgument(1);
            return Mono.just(prompt.contains("Venue Best") ? Verdicts.ideal()
                    : Verdicts.of(prompt.contains("Venue Middle") ? SettingMatch.EXACT : SettingMatch.DRESSABLE));
        });
        when(search.search(any())).thenReturn(Mono.just(List.of(hit("Middle"), hit("Best"), hit("Worst"))));
    }

    private static SearchResult hit(String name) {
        return new SearchResult("Venue " + name, "https://" + name.toLowerCase() + ".example.com/", "Excerpt about " + name, "parallel");
    }

    private JsonNode json(org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner, String area) {
        Map<String, Object> body = area == null ? Map.of("title", "Neon Nights") : Map.of("title", "Neon Nights", "locationArea", area);
        return json(owner.client().post().uri("/api/projects").bodyValue(body).exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String scene(Account owner, String projectId) {
        return json(owner.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("sceneNumber", 1, "title", "Rooftop", "sourceText", "INT. ROOFTOP BAR - NIGHT."))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String sceneWithArea(Account owner) {
        return scene(owner, project(owner, "Brooklyn, New York"));
    }

    // --- parse ----------------------------------------------------------------------------------

    @Test
    void parsingExtractsAndStoresTheRequirements() {
        Account ada = register("Ada");
        String scene = sceneWithArea(ada);

        ada.client().post().uri("/api/scenes/" + scene + "/parse").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.parseStatus").isEqualTo("PARSED")
                .jsonPath("$.requirements.settingType").isEqualTo("rooftop bar")
                .jsonPath("$.requirements.acousticSensitivity").isEqualTo("HIGH")
                .jsonPath("$.requirements.estimatedCastAndCrewSize").isEqualTo(12);
        ada.client().get().uri("/api/scenes/" + scene).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.parseStatus").isEqualTo("PARSED");
    }

    @Test
    void anUnusableExtractionIsA502AndTheSceneIsMarkedFailed() {
        when(llm.generate(any(), any(), eq(SceneRequirements.class)))
                .thenReturn(Mono.error(new LlmException(Kind.INVALID_OUTPUT, "SECRET model output")));
        Account ada = register("Ada");
        String scene = sceneWithArea(ada);

        ada.client().post().uri("/api/scenes/" + scene + "/parse").exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentType(PROBLEM)
                .expectBody()
                .jsonPath("$.retryable").isEqualTo(true)
                .jsonPath("$.detail").value(d -> assertThat(d.toString()).doesNotContain("SECRET"));
        ada.client().get().uri("/api/scenes/" + scene).exchange()
                .expectBody().jsonPath("$.parseStatus").isEqualTo("FAILED");
    }

    // --- parse a whole project ------------------------------------------------------------------

    private String sceneNumbered(Account owner, String projectId, int number, String text) {
        return json(owner.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("sceneNumber", number, "title", "Scene " + number, "sourceText", text))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String parseStatus(Account owner, String sceneId) {
        return json(owner.client().get().uri("/api/scenes/" + sceneId).exchange().expectStatus().isOk()).path("parseStatus").asText();
    }

    @Test
    void parsingAProjectAnalysesItsPendingScenesAndMarksTheOnesTheModelCannotRead() {
        when(llm.generate(any(), any(), eq(SceneRequirements.class))).thenAnswer(call -> ((String) call.getArgument(1)).contains("GIBBERISH")
                ? Mono.error(new LlmException(Kind.INVALID_OUTPUT, "unusable"))
                : Mono.just(REQUIREMENTS));
        Account ada = register("Ada");
        String project = project(ada, "Brooklyn, New York");
        String one = sceneNumbered(ada, project, 1, "INT. ROOFTOP BAR - NIGHT.");
        String two = sceneNumbered(ada, project, 2, "GIBBERISH");
        String three = sceneNumbered(ada, project, 3, "EXT. PIER - DAWN.");
        ada.client().post().uri("/api/scenes/" + three + "/parse").exchange().expectStatus().isOk();
        org.mockito.Mockito.clearInvocations(llm);

        JsonNode result = json(ada.client().post().uri("/api/projects/" + project + "/scenes/parse").exchange().expectStatus().isOk());

        assertThat(result.path("parsed").asInt()).isEqualTo(1);
        assertThat(result.path("failed").asInt()).isEqualTo(1);
        assertThat(result.path("remaining").asInt()).isZero();
        assertThat(parseStatus(ada, one)).isEqualTo("PARSED");
        assertThat(parseStatus(ada, two)).isEqualTo("FAILED");
        // The scene already analysed is not analysed again, and a failed one is not retried by the next run.
        JsonNode again = json(ada.client().post().uri("/api/projects/" + project + "/scenes/parse").exchange().expectStatus().isOk());
        assertThat(again.toString()).isEqualTo("{\"parsed\":0,\"failed\":0,\"remaining\":0}");
    }

    @Test
    void aProjectParseTakesALimitedNumberOfScenesAndSaysHowManyAreLeft() {
        Account ada = register("Ada");
        String project = project(ada, "Brooklyn, New York");
        for (int number = 1; number <= 22; number++) {
            sceneNumbered(ada, project, number, "INT. ROOM " + number + " - DAY.");
        }

        JsonNode first = json(ada.client().post().uri("/api/projects/" + project + "/scenes/parse").exchange().expectStatus().isOk());
        assertThat(first.path("parsed").asInt()).isEqualTo(20);
        assertThat(first.path("remaining").asInt()).isEqualTo(2);

        JsonNode second = json(ada.client().post().uri("/api/projects/" + project + "/scenes/parse").exchange().expectStatus().isOk());
        assertThat(second.path("parsed").asInt()).isEqualTo(2);
        assertThat(second.path("remaining").asInt()).isZero();
    }

    @Test
    void aProjectParseThatGetsNowhereReportsWhy() {
        when(llm.generate(any(), any(), eq(SceneRequirements.class)))
                .thenReturn(Mono.error(new LlmException(Kind.UNAVAILABLE, "SECRET upstream detail")));
        Account ada = register("Ada");
        String project = project(ada, "Brooklyn, New York");
        String scene = sceneNumbered(ada, project, 1, "INT. ROOFTOP BAR - NIGHT.");

        ada.client().post().uri("/api/projects/" + project + "/scenes/parse").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentType(PROBLEM)
                .expectBody().jsonPath("$.detail").value(d -> assertThat(d.toString()).doesNotContain("SECRET"));
        assertThat(parseStatus(ada, scene)).isEqualTo("PENDING");
    }

    @Test
    void anotherUsersProjectCannotBeParsed() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String graceProject = project(grace, "Brooklyn, New York");
        String graceScene = sceneNumbered(grace, graceProject, 1, "INT. ROOFTOP BAR - NIGHT.");

        ada.client().post().uri("/api/projects/" + graceProject + "/scenes/parse").exchange().expectStatus().isNotFound();

        assertThat(parseStatus(grace, graceScene)).isEqualTo("PENDING");
        verifyNoInteractions(llm);
    }

    // --- scout ----------------------------------------------------------------------------------

    @Test
    void scoutingSavesTheAssessedVenuesBestFirstAndListsThemOnTheScene() {
        Account ada = register("Ada");
        String scene = sceneWithArea(ada);

        JsonNode result = json(ada.client().post().uri("/api/scenes/" + scene + "/scout").exchange().expectStatus().isOk());

        assertThat(result.path("alreadySaved").asInt()).isZero();
        assertThat(result.path("unassessed").asInt()).isZero();
        assertThat(result.path("notVenues").asInt()).isZero();
        assertThat(result.path("unsuitable").asInt()).isZero();
        JsonNode added = result.path("added");
        assertThat(added).hasSize(3);
        assertThat(added.get(0).path("name").asText()).isEqualTo("Venue Best");
        assertThat(added.get(0).path("fitScore").asInt()).isEqualTo(94);
        assertThat(added.get(0).path("bookingFriction").asText()).isEqualTo("COMMERCIAL");
        assertThat(added.get(0).path("footprintWarnings").get(0).asText()).isEqualTo("Lift access only");
        assertThat(added.get(0).path("sourceProvider").asText()).isEqualTo("parallel");
        assertThat(added.get(0).path("status").asText()).isEqualTo("SUGGESTED");
        assertThat(added.findValuesAsText("name")).containsExactly("Venue Best", "Venue Middle", "Venue Worst");

        JsonNode listed = json(ada.client().get().uri("/api/scenes/" + scene + "/locations").exchange().expectStatus().isOk());
        assertThat(listed.path("items").findValuesAsText("name")).containsExactly("Venue Best", "Venue Middle", "Venue Worst");
    }

    @Test
    void scoutingSearchesTheProjectsAreaWithTheRequestedLimit() {
        Account ada = register("Ada");
        String scene = sceneWithArea(ada);

        ada.client().post().uri("/api/scenes/" + scene + "/scout?maxResults=5").exchange().expectStatus().isOk();

        ArgumentCaptor<LocationSearchRequest> request = ArgumentCaptor.forClass(LocationSearchRequest.class);
        verify(search).search(request.capture());
        assertThat(request.getValue().area()).isEqualTo("Brooklyn, New York");
        assertThat(request.getValue().maxResults()).isEqualTo(5);
        assertThat(request.getValue().requirements()).isEqualTo(REQUIREMENTS);
    }

    @Test
    void scoutingAgainOnlyAddsWhatIsNewAndKeepsTheUsersShortlist() {
        Account ada = register("Ada");
        String scene = sceneWithArea(ada);
        JsonNode first = json(ada.client().post().uri("/api/scenes/" + scene + "/scout").exchange().expectStatus().isOk());
        String best = first.path("added").get(0).path("id").asText();
        ada.client().put().uri("/api/locations/" + best).bodyValue(Map.of("status", "SHORTLISTED", "notes", "Call them")).exchange()
                .expectStatus().isOk();

        JsonNode second = json(ada.client().post().uri("/api/scenes/" + scene + "/scout").exchange().expectStatus().isOk());

        assertThat(second.path("added")).isEmpty();
        assertThat(second.path("alreadySaved").asInt()).isEqualTo(3);
        ada.client().get().uri("/api/locations/" + best).exchange()
                .expectBody().jsonPath("$.status").isEqualTo("SHORTLISTED").jsonPath("$.notes").isEqualTo("Call them");
    }

    @Test
    void aVenueTheModelCannotAssessIsReportedAsUnassessedNotAFailure() {
        when(llm.generate(any(), any(), eq(VenueVerdict.class))).thenAnswer(call -> {
            String prompt = call.getArgument(1);
            return prompt.contains("Venue Worst")
                    ? Mono.error(new LlmException(Kind.INVALID_OUTPUT, "unusable"))
                    : Mono.just(Verdicts.of(SettingMatch.EXACT));
        });
        Account ada = register("Ada");

        JsonNode result = json(ada.client().post().uri("/api/scenes/" + sceneWithArea(ada) + "/scout").exchange().expectStatus().isOk());

        assertThat(result.path("added")).hasSize(2);
        assertThat(result.path("unassessed").asInt()).isEqualTo(1);
    }

    @Test
    void aVenueScoredZeroIsLeftOutAndCounted() {
        when(llm.generate(any(), any(), eq(VenueVerdict.class))).thenAnswer(call -> {
            boolean elsewhere = call.<String>getArgument(1).contains("Venue Worst");
            return Mono.just(elsewhere ? Verdicts.elsewhere(null) : Verdicts.of(SettingMatch.EXACT));
        });
        Account ada = register("Ada");

        JsonNode result = json(ada.client().post().uri("/api/scenes/" + sceneWithArea(ada) + "/scout").exchange().expectStatus().isOk());

        assertThat(result.path("added")).hasSize(2);
        assertThat(result.path("unsuitable").asInt()).isEqualTo(1);
    }

    @Test
    void aDirectoryOfVenuesIsLeftOutAndCounted() {
        when(llm.generate(any(), any(), eq(VenueVerdict.class))).thenAnswer(call -> {
            boolean directory = call.<String>getArgument(1).contains("Venue Worst");
            return Mono.just(directory ? Verdicts.directory() : Verdicts.of(SettingMatch.EXACT));
        });
        Account ada = register("Ada");

        JsonNode result = json(ada.client().post().uri("/api/scenes/" + sceneWithArea(ada) + "/scout").exchange().expectStatus().isOk());

        assertThat(result.path("added")).hasSize(2);
        assertThat(result.path("added").findValuesAsText("name")).noneMatch(name -> name.contains("Worst"));
        assertThat(result.path("notVenues").asInt()).isEqualTo(1);
    }

    @Test
    void aProjectWithoutAnAreaIsA409AndNothingExpensiveHappens() {
        Account ada = register("Ada");
        String scene = scene(ada, project(ada, null));

        ada.client().post().uri("/api/scenes/" + scene + "/scout").exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentType(PROBLEM)
                .expectBody().jsonPath("$.detail").value(d -> assertThat(d.toString()).contains("location area"));
        verifyNoInteractions(llm, search);
    }

    @Test
    void maxResultsMustBeBetweenOneAndTwenty() {
        Account ada = register("Ada");
        String scene = sceneWithArea(ada);

        for (String bad : new String[] {"0", "21", "-3", "many"}) {
            ada.client().post().uri("/api/scenes/" + scene + "/scout?maxResults=" + bad).exchange()
                    .expectStatus().isBadRequest().expectHeader().contentType(PROBLEM);
        }
        verifyNoInteractions(llm, search);
    }

    // --- failures of the providers ----------------------------------------------------------------

    @Test
    void anLlmOutageIsA503TellingTheClientToRetryWithoutLeakingDetails() {
        when(llm.generate(any(), any(), eq(SceneRequirements.class)))
                .thenReturn(Mono.error(new LlmException(Kind.UNAVAILABLE, "SECRET upstream detail")));
        Account ada = register("Ada");

        ada.client().post().uri("/api/scenes/" + sceneWithArea(ada) + "/scout").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "30")
                .expectHeader().contentType(PROBLEM)
                .expectBody()
                .jsonPath("$.retryable").isEqualTo(true)
                .jsonPath("$.detail").value(d -> assertThat(d.toString()).doesNotContain("SECRET"));
        verifyNoInteractions(search);
    }

    @Test
    void aSearchOutageIsA503() {
        when(search.search(any())).thenReturn(Mono.error(new SearchException(SearchException.Kind.UNAVAILABLE, "SECRET")));
        Account ada = register("Ada");

        ada.client().post().uri("/api/scenes/" + sceneWithArea(ada) + "/scout").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.detail").value(d -> assertThat(d.toString()).contains("search service").doesNotContain("SECRET"));
    }

    @Test
    void aRejectedProviderKeyIsA502ThatIsNotWorthRetrying() {
        when(search.search(any())).thenReturn(Mono.error(new SearchException(SearchException.Kind.AUTHENTICATION, "bad key")));
        Account ada = register("Ada");

        ada.client().post().uri("/api/scenes/" + sceneWithArea(ada) + "/scout").exchange()
                .expectStatus().isEqualTo(502)
                .expectBody().jsonPath("$.retryable").isEqualTo(false);
    }

    // --- access -----------------------------------------------------------------------------------

    @Test
    void anotherUsersSceneIsA404ForBothEndpointsAndCostsNothing() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String graceScene = sceneWithArea(grace);

        ada.client().post().uri("/api/scenes/" + graceScene + "/parse").exchange().expectStatus().isNotFound();
        ada.client().post().uri("/api/scenes/" + graceScene + "/scout").exchange().expectStatus().isNotFound().expectHeader().contentType(PROBLEM);
        verifyNoInteractions(llm, search);
    }

    @Test
    void scoutingNeedsALogin() {
        web.post().uri("/api/scenes/" + java.util.UUID.randomUUID() + "/scout").exchange().expectStatus().isUnauthorized();
        web.post().uri("/api/scenes/" + java.util.UUID.randomUUID() + "/parse").exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(llm, search);
    }

    // --- filters --------------------------------------------------------------------------------

    @Test
    void aProjectsScoutingFiltersAreSetByEditorsReadByTheCrewAndChecked() {
        Account ada = register("Ada");
        Account vera = register("Vera");
        Account outsider = register("Mallory");
        String project = project(ada, "Brooklyn, New York");
        ada.client().post().uri("/api/projects/" + project + "/members").bodyValue(Map.of("email", vera.email(), "role", "VIEWER"))
                .exchange().expectStatus().isCreated();
        String uri = "/api/projects/" + project + "/scout-filters";

        JsonNode empty = json(ada.client().get().uri(uri).exchange().expectStatus().isOk());
        assertThat(empty.path("radiusKm").isNull()).isTrue();
        assertThat(empty.path("excludedTypes")).isEmpty();

        JsonNode saved = json(ada.client().put().uri(uri).bodyValue(Map.of("baseAddress", " Bedford Ave ", "radiusKm", 3,
                "maxBudget", 1500, "excludedTypes", List.of("church", " church ", "nightclub"), "includePrivate", false))
                .exchange().expectStatus().isOk());
        assertThat(saved.path("baseAddress").asText()).isEqualTo("Bedford Ave");
        assertThat(saved.path("excludedTypes")).hasSize(2);
        assertThat(json(vera.client().get().uri(uri).exchange().expectStatus().isOk()).path("maxBudget").asInt()).isEqualTo(1500);

        ada.client().put().uri(uri).bodyValue(Map.of("radiusKm", 3)).exchange().expectStatus().isBadRequest();
        ada.client().put().uri(uri).bodyValue(Map.of("baseLatitude", 40.7)).exchange().expectStatus().isBadRequest();
        ada.client().put().uri(uri).bodyValue(Map.of("baseAddress", "x", "radiusKm", 500)).exchange().expectStatus().isBadRequest();
        vera.client().put().uri(uri).bodyValue(Map.of()).exchange().expectStatus().isForbidden();
        outsider.client().get().uri(uri).exchange().expectStatus().isNotFound();
        outsider.client().put().uri(uri).bodyValue(Map.of()).exchange().expectStatus().isNotFound();
    }

    @Test
    void aRunsFiltersReachTheSearchAndWhatTheyLeaveOutIsCounted() {
        when(llm.generate(any(), any(), eq(VenueVerdict.class))).thenAnswer(call -> {
            boolean club = call.<String>getArgument(1).contains("Venue Worst");
            return Mono.just(club
                    ? new VenueVerdict(true, "Club Neon", null, false, SettingMatch.EXACT, VenueVerdict.Evidence.UNKNOWN,
                    VenueVerdict.Evidence.UNKNOWN, VenueVerdict.Evidence.UNKNOWN, VenueVerdict.Evidence.UNKNOWN,
                    VenueVerdict.Evidence.UNKNOWN, "Fits", com.cinescout.domain.BookingFriction.COMMERCIAL, null, List.of(), List.of(),
                    "nightclub", null)
                    : Verdicts.of(SettingMatch.EXACT));
        });
        Account ada = register("Ada");

        JsonNode result = json(ada.client().post().uri("/api/scenes/" + sceneWithArea(ada) + "/scout")
                .bodyValue(Map.of("filters", Map.of("excludedTypes", List.of("Nightclub")))).exchange().expectStatus().isOk());

        assertThat(result.path("added")).hasSize(2);
        assertThat(result.path("filteredOut").path("excludedType").asInt()).isEqualTo(1);
        ArgumentCaptor<LocationSearchRequest> request = ArgumentCaptor.forClass(LocationSearchRequest.class);
        verify(search).search(request.capture());
        assertThat(request.getValue().hints().excludedTypes()).containsExactly("Nightclub");
        ada.client().post().uri("/api/scenes/" + sceneWithArea(ada) + "/scout")
                .bodyValue(Map.of("filters", Map.of("radiusKm", 2))).exchange().expectStatus().isBadRequest();
    }
}
