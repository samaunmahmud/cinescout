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

/** A venue's draft risk assessment, downloaded as a PDF and read back. */
class RiskAssessmentApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    @Test
    void theDraftIsWrittenFromTheSceneTheRecceAndTheLogistics() throws IOException {
        Account ada = register("Ada");
        String project = json(ada.client().post().uri("/api/projects").bodyValue(Map.of("title", "The Night Ferry", "locationArea", "London"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        String scene = json(ada.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("sceneNumber", 2, "title", "Ferry deck", "sourceText", "EXT. FERRY DECK - NIGHT\nWaves crash."))
                .exchange().expectStatus().isCreated()).path("id").asText();
        String venue = json(ada.client().post().uri("/api/scenes/" + scene + "/locations")
                .bodyValue(Map.of("name", "Old Quay Pier", "address", "1 Dock St, London")).exchange().expectStatus().isCreated()).path("id").asText();
        ada.client().patch().uri("/api/locations/" + venue + "/recce")
                .bodyValue(Map.of("sockets", 0, "ceilingHeightM", 6, "phoneSignal", "POOR", "toilets", false, "notes", "Gate code 4411"))
                .exchange().expectStatus().isOk();
        jdbc.update("UPDATE locations SET logistics_json = ?::jsonb WHERE id = ?::uuid",
                "{\"environment\": {\"nearbyServices\": [{\"kind\": \"HOSPITAL\", \"name\": \"Royal London Hospital\", \"distanceMeters\": 900}]}}",
                venue);

        byte[] pdf = ada.client().get().uri("/api/locations/" + venue + "/risk-assessment").exchange().expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_PDF)
                .expectHeader().value("Content-Disposition", value -> assertThat(value).contains("risk-assessment-old-quay-pier.pdf"))
                .expectBody(byte[].class).returnResult().getResponseBody();
        String text;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }

        assertThat(text).contains("RISK ASSESSMENT", "Scene: EXT. FERRY DECK - NIGHT", "Royal London Hospital, 900 m away",
                "The recce found no sockets", "Working at height", "6 m ceiling", "Water nearby", "Weather", "Night work and fatigue",
                "Calling for help", "poor phone signal", "Welfare", "no toilets", "Gate code 4411");

        register("Mal").client().get().uri("/api/locations/" + venue + "/risk-assessment").exchange().expectStatus().isNotFound();
    }
}
