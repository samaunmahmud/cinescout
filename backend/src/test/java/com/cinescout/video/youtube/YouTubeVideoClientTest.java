package com.cinescout.video.youtube;

import com.cinescout.video.Video;
import com.cinescout.video.VideoException;
import com.cinescout.video.VideoException.Kind;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Bodies follow the YouTube Data API v3 reference for search.list and its error format. */
class YouTubeVideoClientTest {

    private static final String RESULTS = """
            {"kind":"youtube#searchListResponse","pageInfo":{"totalResults":2,"resultsPerPage":6},
             "items":[
              {"kind":"youtube#searchResult","id":{"kind":"youtube#video","videoId":"dQw4w9WgXcQ"},
               "snippet":{"publishedAt":"2024-05-01T12:00:00Z","channelId":"UC1","title":"Rooftop tour &amp; views at Joe&#39;s",
                          "description":"...","channelTitle":"NYC &quot;Rooftops&quot;",
                          "thumbnails":{"medium":{"url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg"}}}},
              {"kind":"youtube#searchResult","id":{"kind":"youtube#video","videoId":"not a real id"},
               "snippet":{"publishedAt":"2024-05-01T12:00:00Z","title":"Broken","channelTitle":"X"}},
              {"kind":"youtube#searchResult","id":{"kind":"youtube#video","videoId":"abcDEF12_-3"},
               "snippet":{"publishedAt":"not a date","title":"  Second  ","channelTitle":"Y"}}
             ]}""";

    @RegisterExtension
    static WireMockExtension api = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private YouTubeVideoClient client(Duration timeout) {
        YouTubeProperties props = new YouTubeProperties("secret-key", api.baseUrl(), timeout);
        return new YouTubeVideoClient(WebClient.builder().baseUrl(api.baseUrl()).build(), props, new ObjectMapper());
    }

    private YouTubeVideoClient client() {
        return client(Duration.ofSeconds(5));
    }

    private void stub(int status, String body) {
        api.stubFor(get(urlPathEqualTo("/youtube/v3/search")).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    void asksForEmbeddableSafeVideosWithTheKeyInAHeaderNeverInTheUrl() {
        stub(200, RESULTS);

        client().search("Joe's Bar & Grill Brooklyn", 6).block();

        api.verify(getRequestedFor(urlPathEqualTo("/youtube/v3/search"))
                .withQueryParam("q", equalTo("Joe's Bar & Grill Brooklyn"))
                .withQueryParam("part", equalTo("snippet"))
                .withQueryParam("type", equalTo("video"))
                .withQueryParam("videoEmbeddable", equalTo("true"))
                .withQueryParam("safeSearch", equalTo("strict"))
                .withQueryParam("maxResults", equalTo("6"))
                .withQueryParam("key", absent())
                .withHeader("X-Goog-Api-Key", equalTo("secret-key")));
    }

    @Test
    void unescapesTitlesAndDropsHitsWithoutAWellFormedId() {
        stub(200, RESULTS);

        List<Video> videos = client().search("q", 6).block();

        assertThat(videos).containsExactly(
                new Video("dQw4w9WgXcQ", "Rooftop tour & views at Joe's", "NYC \"Rooftops\"", Instant.parse("2024-05-01T12:00:00Z")),
                new Video("abcDEF12_-3", "Second", "Y", null));
    }

    @Test
    void noItemsIsNoVideos() {
        stub(200, "{\"items\":[]}");
        assertThat(client().search("q", 6).block()).isEmpty();
        stub(200, "{}");
        assertThat(client().search("q", 6).block()).isEmpty();
    }

    private Kind kindOf(int status, String body) {
        stub(status, body);
        try {
            client().search("q", 6).block();
        } catch (VideoException e) {
            assertThat(e.getMessage()).doesNotContain("secret-key");
            return e.kind();
        }
        throw new AssertionError("expected a VideoException");
    }

    private static String error(int code, String reason, String message) {
        return "{\"error\":{\"code\":" + code + ",\"message\":\"" + message + "\",\"errors\":[{\"message\":\"" + message
                + "\",\"domain\":\"youtube.quota\",\"reason\":\"" + reason + "\"}]}}";
    }

    @Test
    void classifiesFailuresByGooglesReason() {
        assertThat(kindOf(403, error(403, "quotaExceeded", "The request cannot be completed because you have exceeded your quota.")))
                .isEqualTo(Kind.QUOTA_EXCEEDED);
        assertThat(kindOf(400, error(400, "badRequest", "API key not valid. Please pass a valid API key.")))
                .isEqualTo(Kind.AUTHENTICATION);
        assertThat(kindOf(403, error(403, "accessNotConfigured", "YouTube Data API v3 has not been used in project 1")))
                .isEqualTo(Kind.AUTHENTICATION);
        assertThat(kindOf(400, error(400, "invalidSearchFilter", "The request contains an invalid combination of search filters")))
                .isEqualTo(Kind.INVALID_REQUEST);
        assertThat(kindOf(503, "<html>down</html>")).isEqualTo(Kind.UNAVAILABLE);
        assertThat(kindOf(429, "")).isEqualTo(Kind.QUOTA_EXCEEDED);
    }

    @Test
    void aSlowAnswerIsUnavailable() {
        api.stubFor(get(urlPathEqualTo("/youtube/v3/search")).willReturn(aResponse().withFixedDelay(1500)
                .withHeader("Content-Type", "application/json").withBody(RESULTS)));

        assertThatThrownBy(() -> client(Duration.ofMillis(300)).search("q", 6).block())
                .isInstanceOfSatisfying(VideoException.class, e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));
    }

    @Test
    void theKeyIsNeverPrinted() {
        assertThat(new YouTubeProperties("secret-key", "https://x", Duration.ofSeconds(1)).toString()).doesNotContain("secret-key");
    }
}
