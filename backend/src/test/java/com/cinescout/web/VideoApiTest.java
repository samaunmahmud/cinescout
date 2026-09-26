package com.cinescout.web;

import com.cinescout.video.Video;
import com.cinescout.video.VideoException;
import com.cinescout.video.VideoSearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Venue videos end to end (HTTP, security, the real service and cache, PostgreSQL) with only YouTube mocked. */
@SpringBootTest(properties = {
        "cinescout.video.youtube.api-key=dummy",
        "cinescout.resilience.initial-backoff=1ms", "cinescout.resilience.max-backoff=5ms",
        "cinescout.resilience.breaker-window-size=100", "cinescout.resilience.breaker-minimum-calls=100"
})
class VideoApiTest extends ApiTest {

    private static final Video TOUR = new Video("dQw4w9WgXcQ", "Rooftop tour", "NYC Rooftops", Instant.parse("2024-05-01T12:00:00Z"));

    @MockitoBean VideoSearchClient youtube;

    @BeforeEach
    void defaultAnswer() {
        when(youtube.search(anyString(), anyInt())).thenReturn(Mono.just(List.of(TOUR)));
    }

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String location(Account owner, Map<String, Object> venue) {
        String project = json(owner.client().post().uri("/api/projects")
                .bodyValue(Map.of("title", "Neon Nights", "locationArea", "Brooklyn, New York"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        String scene = json(owner.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("title", "Rooftop", "sourceText", "EXT. ROOFTOP - NIGHT."))
                .exchange().expectStatus().isCreated()).path("id").asText();
        return json(owner.client().post().uri("/api/scenes/" + scene + "/locations").bodyValue(venue)
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    @Test
    void searchesForTheVenueByNameAndAddressAndReturnsTheVideos() {
        Account ada = register("Ada");
        String id = location(ada, Map.of("name", "Wythe Hotel", "address", "80 Wythe Ave, Brooklyn"));

        ada.client().get().uri("/api/locations/" + id + "/videos").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.query").isEqualTo("Wythe Hotel 80 Wythe Ave, Brooklyn")
                .jsonPath("$.fetchedAt").exists()
                .jsonPath("$.videos[0].id").isEqualTo("dQw4w9WgXcQ")
                .jsonPath("$.videos[0].title").isEqualTo("Rooftop tour")
                .jsonPath("$.videos[0].channel").isEqualTo("NYC Rooftops")
                .jsonPath("$.videos[0].publishedAt").isEqualTo("2024-05-01T12:00:00Z");
        verify(youtube).search(eq("Wythe Hotel 80 Wythe Ave, Brooklyn"), eq(6));
    }

    @Test
    void withoutAnAddressTheProjectsAreaSaysWhere() {
        Account ada = register("Ada");
        String id = location(ada, Map.of("name", "Industry City"));

        ada.client().get().uri("/api/locations/" + id + "/videos").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.query").isEqualTo("Industry City Brooklyn, New York");
    }

    @Test
    void resultsAreCachedSoTheQuotaIsSpentOncePerVenue() {
        Account ada = register("Ada");
        String id = location(ada, Map.of("name", "Wythe Hotel"));

        String first = json(ada.client().get().uri("/api/locations/" + id + "/videos").exchange().expectStatus().isOk()).toString();
        String second = json(ada.client().get().uri("/api/locations/" + id + "/videos").exchange().expectStatus().isOk()).toString();

        verify(youtube, times(1)).search(anyString(), anyInt());
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }

    @Test
    void anOldCacheIsSearchedAgain() {
        Account ada = register("Ada");
        String id = location(ada, Map.of("name", "Wythe Hotel"));
        ada.client().get().uri("/api/locations/" + id + "/videos").exchange().expectStatus().isOk();
        jdbc.update("UPDATE locations SET videos_fetched_at = now() - interval '8 days'");

        ada.client().get().uri("/api/locations/" + id + "/videos").exchange().expectStatus().isOk();

        verify(youtube, times(2)).search(anyString(), anyInt());
    }

    @Test
    void aCacheForAnotherQueryIsNotUsed() {
        Account ada = register("Ada");
        String id = location(ada, Map.of("name", "Wythe Hotel"));
        ada.client().get().uri("/api/locations/" + id + "/videos").exchange().expectStatus().isOk();
        // As if the venue had been renamed since.
        jdbc.update("UPDATE locations SET name = 'The Wythe'");

        ada.client().get().uri("/api/locations/" + id + "/videos").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.query").isEqualTo("The Wythe Brooklyn, New York");
        verify(youtube).search(eq("The Wythe Brooklyn, New York"), anyInt());
    }

    @Test
    void someoneElsesLocationIsA404AndCostsNoSearch() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String id = location(ada, Map.of("name", "Wythe Hotel"));

        grace.client().get().uri("/api/locations/" + id + "/videos").exchange().expectStatus().isNotFound();
        verify(youtube, never()).search(anyString(), anyInt());
    }

    @Test
    void aSpentQuotaIsA503ThatSaysSoAndIsNotCached() {
        Account ada = register("Ada");
        String id = location(ada, Map.of("name", "Wythe Hotel"));
        when(youtube.search(anyString(), anyInt()))
                .thenReturn(Mono.error(new VideoException(VideoException.Kind.QUOTA_EXCEEDED, "YouTube returned HTTP 403 (quotaExceeded)")));

        ada.client().get().uri("/api/locations/" + id + "/videos").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("The video service's daily limit is used up; try again tomorrow")
                .jsonPath("$.retryable").isEqualTo(false);
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT videos_json IS NULL FROM locations", Boolean.class)).isTrue();
    }
}
