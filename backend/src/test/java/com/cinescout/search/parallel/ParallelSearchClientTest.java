package com.cinescout.search.parallel;

import com.cinescout.ai.SearchResult;
import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.search.LocationSearchRequest;
import com.cinescout.search.SearchException;
import com.cinescout.search.SearchException.Kind;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class ParallelSearchClientTest {

    private static final String SEARCH = "/v1/search";

    private static final LocationSearchRequest REQUEST = LocationSearchRequest.of(
            new SceneRequirements("rooftop bar", "neon noir", null, "night", AcousticSensitivity.HIGH, 12),
            "Brooklyn, New York");

    @RegisterExtension
    static WireMockExtension api = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    // Same Jackson defaults as the Spring Boot ObjectMapper the app injects (unknown properties ignored).
    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private ParallelSearchClient client() {
        return client(new ParallelProperties("test-key", api.baseUrl(), null, 1500, Duration.ofSeconds(5)), api.baseUrl());
    }

    private ParallelSearchClient client(ParallelProperties props, String baseUrl) {
        return new ParallelSearchClient(WebClient.create(baseUrl), props, json, validator);
    }

    private void stubSearch(int status, String body) {
        api.stubFor(post(SEARCH).willReturn(aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    private List<SearchResult> search() {
        return client().search(REQUEST).block();
    }

    private static SearchException failureOf(Runnable call) {
        Throwable thrown = catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(SearchException.class);
        return (SearchException) thrown;
    }

    // --- request ------------------------------------------------------------------------------

    @Test
    void sendsTheDocumentedRequest() {
        stubSearch(200, "{\"search_id\":\"s1\",\"results\":[]}");

        search();

        api.verify(postRequestedFor(urlEqualTo(SEARCH))
                .withHeader("x-api-key", equalTo("test-key"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(matchingJsonPath("$.objective", com.github.tomakehurst.wiremock.client.WireMock.containing("Brooklyn, New York")))
                .withRequestBody(matchingJsonPath("$.search_queries[0]", equalTo("rooftop bar Brooklyn, New York film location hire")))
                .withRequestBody(matchingJsonPath("$.search_queries.length()", equalTo("3")))
                .withRequestBody(matchingJsonPath("$.advanced_settings.max_results", equalTo("10")))
                .withRequestBody(matchingJsonPath("$.advanced_settings.excerpt_settings.max_chars_per_result", equalTo("1500"))));
    }

    @Test
    void leavesTheModeToParallelUnlessConfigured() {
        stubSearch(200, "{\"results\":[]}");

        search();

        api.verify(0, postRequestedFor(urlEqualTo(SEARCH)).withRequestBody(matchingJsonPath("$.mode")));
    }

    @Test
    void sendsTheConfiguredModeAndResultLimit() {
        stubSearch(200, "{\"results\":[]}");
        ParallelProperties props = new ParallelProperties("test-key", api.baseUrl(), "fast", 800, Duration.ofSeconds(5));

        client(props, api.baseUrl()).search(new LocationSearchRequest(REQUEST.requirements(), "Brooklyn", 5)).block();

        api.verify(postRequestedFor(urlEqualTo(SEARCH))
                .withRequestBody(matchingJsonPath("$.mode", equalTo("fast")))
                .withRequestBody(matchingJsonPath("$.advanced_settings.max_results", equalTo("5")))
                .withRequestBody(matchingJsonPath("$.advanced_settings.excerpt_settings.max_chars_per_result", equalTo("800"))));
    }

    // --- results --------------------------------------------------------------------------------

    @Test
    void mapsHitsIntoProviderNeutralSearchResults() {
        stubSearch(200, """
                {"search_id": "s1", "session_id": "x", "usage": [{"name": "sku_search", "count": 1}],
                 "results": [
                   {"url": "https://www.santanarooftop.com/hire", "title": "Santana Rooftop",
                    "publish_date": "2025-03-01", "excerpts": ["Rooftop bar with skyline views.", "Film enquiries welcome."]},
                   {"url": "http://example.org/loft", "title": "The Loft", "excerpts": ["Industrial loft."]}
                 ]}
                """);

        assertThat(search()).containsExactly(
                new SearchResult("Santana Rooftop", "https://www.santanarooftop.com/hire",
                        "Rooftop bar with skyline views.\n\nFilm enquiries welcome.", "parallel"),
                new SearchResult("The Loft", "http://example.org/loft", "Industrial loft.", "parallel"));
    }

    @Test
    void aMissingTitleFallsBackToTheHostAndMissingExcerptsBecomeNull() {
        stubSearch(200, """
                {"results": [{"url": "https://venue.example.com/page", "title": null, "excerpts": []},
                             {"url": "https://other.example.com/", "title": "  ", "excerpts": [" ", null]}]}
                """);

        assertThat(search()).containsExactly(
                new SearchResult("venue.example.com", "https://venue.example.com/page", null, "parallel"),
                new SearchResult("other.example.com", "https://other.example.com/", null, "parallel"));
    }

    @Test
    void dropsHitsThatCouldNotBeStoredOrRenderedSafely() {
        stubSearch(200, """
                {"results": [
                  {"url": "javascript:alert(1)", "title": "XSS", "excerpts": ["x"]},
                  {"url": "file:///etc/passwd", "title": "Local", "excerpts": ["x"]},
                  {"url": "not a url", "title": "Junk", "excerpts": ["x"]},
                  {"title": "No URL", "excerpts": ["x"]},
                  {"url": "  ", "title": "Blank URL", "excerpts": ["x"]},
                  {"url": "https://good.example.com/", "title": "Good", "excerpts": ["ok"]}
                ]}
                """);

        assertThat(search()).extracting(SearchResult::title).containsExactly("Good");
    }

    @Test
    void returnsEachPageOnceKeepingTheBestRankedHit() {
        stubSearch(200, """
                {"results": [
                  {"url": "https://a.example.com/", "title": "First", "excerpts": ["1"]},
                  {"url": "https://b.example.com/", "title": "Other", "excerpts": ["2"]},
                  {"url": "https://a.example.com/", "title": "Duplicate", "excerpts": ["3"]}
                ]}
                """);

        assertThat(search()).extracting(SearchResult::title).containsExactly("First", "Other");
    }

    @Test
    void neverReturnsMoreThanRequested() {
        stubSearch(200, """
                {"results": [
                  {"url": "https://a.example.com/", "title": "A"}, {"url": "https://b.example.com/", "title": "B"},
                  {"url": "https://c.example.com/", "title": "C"}
                ]}
                """);

        List<SearchResult> results = client()
                .search(new LocationSearchRequest(REQUEST.requirements(), "Brooklyn", 2)).block();

        assertThat(results).extracting(SearchResult::title).containsExactly("A", "B");
    }

    @Test
    void capsAnOverlongExcerpt() {
        stubSearch(200, "{\"results\": [{\"url\": \"https://a.example.com/\", \"title\": \"A\", \"excerpts\": [\""
                + "x".repeat(20_000) + "\"]}]}");

        assertThat(search().get(0).excerpt()).hasSize(5000);
    }

    @Test
    void noResultsIsAnEmptyListNotAnError() {
        stubSearch(200, "{\"search_id\": \"s1\", \"results\": []}");
        assertThat(search()).isEmpty();

        stubSearch(200, "{\"search_id\": \"s1\", \"warnings\": [{\"type\": \"warning\", \"message\": \"adjusted\"}]}");
        assertThat(search()).isEmpty();
    }

    // --- upstream failures ----------------------------------------------------------------------

    @Test
    void aRejectedKeyIsAnAuthenticationFailure() {
        stubSearch(401, "{}");

        SearchException error = failureOf(this::search);

        assertThat(error.kind()).isEqualTo(Kind.AUTHENTICATION);
        assertThat(error.isRetryable()).isFalse();
    }

    @Test
    void throttlingIsRetryable() {
        stubSearch(429, "{}");

        SearchException error = failureOf(this::search);

        assertThat(error.kind()).isEqualTo(Kind.RATE_LIMITED);
        assertThat(error.isRetryable()).isTrue();
    }

    @Test
    void aServerErrorIsRetryableUnavailable() {
        stubSearch(503, "down");

        SearchException error = failureOf(this::search);

        assertThat(error.kind()).isEqualTo(Kind.UNAVAILABLE);
        assertThat(error.isRetryable()).isTrue();
    }

    @Test
    void aRejectedRequestReportsParallelsMessageAndReferenceButNotTheRestOfTheBody() {
        stubSearch(422, "{\"type\":\"error\",\"error\":{\"ref_id\":\"ref_123\",\"message\":\"search_queries must not be empty\","
                + "\"detail\":{\"echo\":\"SECRET-DETAIL\"}}}");

        SearchException error = failureOf(this::search);

        assertThat(error.kind()).isEqualTo(Kind.INVALID_REQUEST);
        assertThat(error.isRetryable()).isFalse();
        assertThat(error.getMessage()).contains("HTTP 422", "search_queries must not be empty", "ref_123")
                .doesNotContain("SECRET-DETAIL");
    }

    @Test
    void anUnparseableErrorBodyIsNotEchoed() {
        stubSearch(400, "<html>internal page with rooftop bar</html>");

        assertThat(failureOf(this::search).getMessage()).doesNotContain("internal page").doesNotContain("rooftop");
    }

    @Test
    void aMalformedSuccessBodyIsUnavailableNotACrash() {
        stubSearch(200, "this is not json");

        assertThat(failureOf(this::search).kind()).isEqualTo(Kind.UNAVAILABLE);
    }

    @Test
    void aSlowAnswerTimesOutAsUnavailable() {
        api.stubFor(post(SEARCH).willReturn(aResponse()
                .withFixedDelay(1500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"results\": []}")));
        ParallelProperties props = new ParallelProperties("test-key", api.baseUrl(), null, 1500, Duration.ofMillis(300));

        SearchException error = failureOf(() -> client(props, api.baseUrl()).search(REQUEST).block());

        assertThat(error.kind()).isEqualTo(Kind.UNAVAILABLE);
        assertThat(error.getMessage()).contains("did not answer");
    }

    @Test
    void aRefusedConnectionIsUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        String baseUrl = "http://localhost:" + closedPort;
        ParallelProperties props = new ParallelProperties("test-key", baseUrl, null, 1500, Duration.ofSeconds(5));

        assertThat(failureOf(() -> client(props, baseUrl).search(REQUEST).block()).kind()).isEqualTo(Kind.UNAVAILABLE);
    }

    @Test
    void errorsNeverContainTheApiKeyOrTheQuery() {
        stubSearch(500, "{}");

        assertThat(failureOf(this::search).getMessage()).doesNotContain("test-key").doesNotContain("Brooklyn");
    }

    // --- misuse ---------------------------------------------------------------------------------

    @Test
    void aNullRequestIsAProgrammingError() {
        assertThatThrownBy(() -> client().search(null).block()).isInstanceOf(IllegalArgumentException.class);
        api.verify(0, postRequestedFor(urlEqualTo(SEARCH)));
    }

    // --- one named venue ------------------------------------------------------------------------

    @Test
    void findsANamedVenueWithItsOwnObjectiveAndQueries() {
        stubSearch(200, """
                {"search_id": "s2", "results": [
                  {"url": "https://www.barblondeau.com/", "title": "Bar Blondeau", "excerpts": ["Rooftop bar at the Wythe Hotel."]}
                ]}
                """);

        List<SearchResult> hits = client().findVenue("Bar Blondeau, Wythe Hotel", "Brooklyn, New York", 2).block();

        assertThat(hits).extracting(SearchResult::url).containsExactly("https://www.barblondeau.com/");
        api.verify(postRequestedFor(urlEqualTo(SEARCH))
                .withRequestBody(matchingJsonPath("$.objective", com.github.tomakehurst.wiremock.client.WireMock.containing(
                        "official website of the venue \"Bar Blondeau, Wythe Hotel\" in Brooklyn, New York")))
                .withRequestBody(matchingJsonPath("$.search_queries[0]", equalTo("Bar Blondeau, Wythe Hotel Brooklyn, New York")))
                .withRequestBody(matchingJsonPath("$.search_queries[1]", equalTo("Bar Blondeau, Wythe Hotel official site")))
                .withRequestBody(matchingJsonPath("$.advanced_settings.max_results", equalTo("2"))));
    }

    @Test
    void aNamedVenueSearchFailsLikeAnyOther() {
        stubSearch(503, "{}");

        assertThat(failureOf(() -> client().findVenue("Bar Blondeau", "Brooklyn", 2).block()).kind())
                .isEqualTo(SearchException.Kind.UNAVAILABLE);
    }

    @Test
    void aNamedVenueSearchNeedsANameAnAreaAndASensibleLimit() {
        assertThatThrownBy(() -> client().findVenue(" ", "Brooklyn", 2).block()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client().findVenue("Bar", null, 2).block()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client().findVenue("Bar", "Brooklyn", 0).block()).isInstanceOf(IllegalArgumentException.class);
        api.verify(0, postRequestedFor(urlEqualTo(SEARCH)));
    }
}
