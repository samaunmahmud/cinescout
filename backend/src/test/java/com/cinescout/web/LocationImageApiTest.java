package com.cinescout.web;

import com.cinescout.imagery.PageImageFinder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A venue's picture, looked up from its web page, through the whole application; only the fetch is replaced. */
class LocationImageApiTest extends ApiTest {

    @MockitoBean PageImageFinder finder;

    @BeforeEach
    void aPageWithAPicture() {
        when(finder.imageOf(anyString())).thenReturn(Mono.just("https://cdn.example/roof.jpg"));
    }

    private String location(Account owner, String sourceUrl) {
        JsonNode project = owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Neon")).exchange()
                .expectStatus().isCreated().expectBody(JsonNode.class).returnResult().getResponseBody();
        JsonNode scene = owner.client().post().uri("/api/projects/" + project.path("id").asText() + "/scenes")
                .bodyValue(Map.of("title", "Roof", "sourceText", "EXT. ROOF - NIGHT")).exchange()
                .expectStatus().isCreated().expectBody(JsonNode.class).returnResult().getResponseBody();
        Map<String, Object> body = sourceUrl == null ? Map.of("name", "Sky Bar") : Map.of("name", "Sky Bar", "sourceUrl", sourceUrl);
        return owner.client().post().uri("/api/scenes/" + scene.path("id").asText() + "/locations").bodyValue(body).exchange()
                .expectStatus().isCreated().expectBody(JsonNode.class).returnResult().getResponseBody().path("id").asText();
    }

    @Test
    void thePictureIsLookedUpOnceAndKept() {
        Account ada = register("Ada");
        String location = location(ada, "https://skybar.example/");
        ada.client().get().uri("/api/locations/" + location).exchange()
                .expectBody().jsonPath("$.imageUrl").isEmpty().jsonPath("$.imageCheckedAt").isEmpty();

        ada.client().post().uri("/api/locations/" + location + "/image").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.imageUrl").isEqualTo("https://cdn.example/roof.jpg").jsonPath("$.imageCheckedAt").isNotEmpty();
        ada.client().post().uri("/api/locations/" + location + "/image").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.imageUrl").isEqualTo("https://cdn.example/roof.jpg");

        verify(finder, times(1)).imageOf("https://skybar.example/");
        ada.client().get().uri("/api/locations/" + location).exchange().expectBody().jsonPath("$.imageUrl").isEqualTo("https://cdn.example/roof.jpg");
    }

    @Test
    void aPageWithoutAPictureIsRememberedAsChecked() {
        when(finder.imageOf(anyString())).thenReturn(Mono.empty());
        Account ada = register("Ada");
        String location = location(ada, "https://skybar.example/");

        ada.client().post().uri("/api/locations/" + location + "/image").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.imageUrl").isEmpty().jsonPath("$.imageCheckedAt").isNotEmpty();
        ada.client().post().uri("/api/locations/" + location + "/image").exchange().expectStatus().isOk();

        verify(finder, times(1)).imageOf(anyString());
    }

    @Test
    void aVenueWithoutAWebPageHasNothingToLookUpAndAnotherUsersIsA404() {
        Account ada = register("Ada");
        String location = location(ada, null);

        JsonNode result = ada.client().post().uri("/api/locations/" + location + "/image").exchange().expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
        assertThat(result.path("imageUrl").isNull()).isTrue();
        verifyNoInteractions(finder);
        register("Grace").client().post().uri("/api/locations/" + location + "/image").exchange().expectStatus().isNotFound();
    }
}
