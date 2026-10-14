package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The letter to a venue's neighbours, downloaded as a PDF and read back. */
class ResidentsLetterApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    @Test
    void theLetterUsesTheScenesDatesAndHoursAndNeverTheOwnersDetails() throws IOException {
        Account ada = register("Ada");
        String project = json(ada.client().post().uri("/api/projects").bodyValue(Map.of("title", "The Night Ferry", "locationArea", "London"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        String scene = json(ada.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("sceneNumber", 1, "title", "INT. DINER", "sourceText", "INT. DINER", "shootDateStart", "2026-11-02"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        ada.client().put().uri("/api/scenes/" + scene + "/shoot-dates")
                .bodyValue(Map.of("shootDateStart", "2026-11-02", "shootDateEnd", "2026-11-02", "callTime", "17:00", "wrapTime", "23:30"))
                .exchange().expectStatus().isOk();
        String venue = json(ada.client().post().uri("/api/scenes/" + scene + "/locations")
                .bodyValue(Map.of("name", "Starlite Diner", "address", "1 Dock St, London")).exchange().expectStatus().isCreated()).path("id").asText();
        ada.client().put().uri("/api/locations/" + venue + "/contact")
                .bodyValue(Map.of("name", "Rita Moss", "phone", "+44 20 7946 0100", "quote", "£800 a day")).exchange().expectStatus().isOk();

        byte[] pdf = ada.client().post().uri("/api/locations/" + venue + "/residents-letter")
                .bodyValue(Map.of("contactPhone", "+44 7700 900123", "parking", "Two trucks on Dock Street."))
                .exchange().expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_PDF)
                .expectHeader().value("Content-Disposition", value -> assertThat(value).contains("letter-to-neighbours-starlite-diner.pdf"))
                .expectBody(byte[].class).returnResult().getResponseBody();
        String text = text(pdf);

        assertThat(text).contains("FILMING NEAR YOU", "The Night Ferry", "Starlite Diner, 1 Dock St, London", "Monday 2 November 2026",
                "From 5:00 PM to 11:30 PM", "Part of the filming happens at night", "Two trucks on Dock Street.", "Ada", "Phone: +44 7700 900123");
        assertThat(text).doesNotContain("Rita Moss", "7946 0100", "£800");

        ada.client().post().uri("/api/locations/" + venue + "/residents-letter").exchange().expectStatus().isOk();
        ada.client().post().uri("/api/locations/" + venue + "/residents-letter").bodyValue(Map.of("contactEmail", "not an email"))
                .exchange().expectStatus().isBadRequest();
        register("Mal").client().post().uri("/api/locations/" + venue + "/residents-letter").exchange().expectStatus().isNotFound();
    }
}
