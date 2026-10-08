package com.cinescout.web;

import com.cinescout.photos.TestPhotos;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import org.springframework.web.reactive.function.BodyInserters;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The production's location pack, downloaded as a PDF and read back. */
class LocationPackApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String scene(Account owner, String project, int number, String title) {
        return json(owner.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("sceneNumber", number, "title", title, "sourceText", title, "shootDateStart", "2026-11-02"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String venue(Account owner, String scene, String name, String status) {
        String venue = json(owner.client().post().uri("/api/scenes/" + scene + "/locations")
                .bodyValue(Map.of("name", name, "address", "1 Dock St, London", "latitude", 51.5, "longitude", -0.08))
                .exchange().expectStatus().isCreated()).path("id").asText();
        owner.client().put().uri("/api/locations/" + venue).bodyValue(Map.of("status", status, "notes", "Owner loves film crews"))
                .exchange().expectStatus().isOk();
        return venue;
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void eachSceneShowsItsConfirmedVenueOrItsBestShortlistWithWhatAProducerNeeds() throws IOException {
        Account ada = register("Ada");
        String project = json(ada.client().post().uri("/api/projects").bodyValue(Map.of("title", "The Night Ferry", "locationArea", "London"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        String terminal = scene(ada, project, 1, "INT. FERRY TERMINAL - NIGHT");
        String confirmed = venue(ada, terminal, "Tannery Hall", "CONFIRMED");
        venue(ada, terminal, "Not Shown Hall", "SHORTLISTED");
        ada.client().put().uri("/api/locations/" + confirmed + "/contact")
                .bodyValue(Map.of("name", "Sal Moss", "phone", "+44 20 7946 0100", "quote", "£800 a day")).exchange().expectStatus().isOk();
        ada.client().patch().uri("/api/locations/" + confirmed + "/recce").bodyValue(Map.of("sockets", 6, "toilets", true))
                .exchange().expectStatus().isOk();
        ada.client().put().uri("/api/locations/" + confirmed + "/availability").bodyValue(Map.of("from", "2026-11-02", "state", "CONFIRMED"))
                .exchange().expectStatus().isOk();
        jdbc.update("UPDATE locations SET fit_score = 82, fit_reason = 'Vast hall with tall windows' WHERE id = ?::uuid", confirmed);
        MultipartBodyBuilder photo = new MultipartBodyBuilder();
        photo.part("file", new ByteArrayResource(TestPhotos.jpeg(120, 80)) {
            @Override
            public String getFilename() {
                return "hall.jpg";
            }
        }).contentType(MediaType.IMAGE_JPEG);
        String photoId = json(ada.client().post().uri("/api/locations/" + confirmed + "/photos").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(photo.build())).exchange().expectStatus().isCreated()).path("photo").path("id").asText();
        ada.client().put().uri("/api/locations/" + confirmed + "/cover").bodyValue(Map.of("photoId", photoId)).exchange().expectStatus().isOk();
        String deck = scene(ada, project, 2, "EXT. FERRY DECK - NIGHT");
        venue(ada, deck, "Old Quay Pier", "SHORTLISTED");
        venue(ada, deck, "Passed On Pier", "REJECTED");
        scene(ada, project, 3, "INT. HARBOUR CAFE - DAWN");

        var response = ada.client().get().uri("/api/projects/" + project + "/location-pack").exchange().expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_PDF)
                .expectHeader().value("Content-Disposition", value -> assertThat(value).contains("The Night Ferry location pack.pdf"))
                .expectBody(byte[].class).returnResult().getResponseBody();
        String pdf = text(response);

        assertThat(pdf).contains("LOCATION PACK", "The Night Ferry", "3 scenes", "2 venues", "Prepared");
        assertThat(pdf).contains("1. INT. FERRY TERMINAL - NIGHT", "Tannery Hall", "Confirmed", "Fit 82/100", "Vast hall with tall windows",
                "Sal Moss", "£800 a day", "openstreetmap.org", "Owner loves film crews", "Sockets", "Toilets", "Mon 2 Nov 2026: confirmed");
        assertThat(pdf).doesNotContain("Not Shown Hall", "Passed On Pier");
        assertThat(pdf).contains("Old Quay Pier", "Shortlisted");
        assertThat(pdf).contains("3. INT. HARBOUR CAFE - DAWN", "No venue confirmed or shortlisted yet.");
        try (PDDocument document = Loader.loadPDF(response)) {
            assertThat(document.getPage(1).getResources().getXObjectNames()).isNotEmpty(); // the cover photo
        }

        register("Mal").client().get().uri("/api/projects/" + project + "/location-pack").exchange().expectStatus().isNotFound();
    }
}
