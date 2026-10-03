package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Location releases end to end: generated from the venue's details, kept as versions, handed to members only. */
class AgreementApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String[] venue(Account owner) {
        String project = json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "The Night Ferry"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        String scene = json(owner.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("sceneNumber", 4, "title", "Diner", "sourceText", "INT. DINER - NIGHT. SECRET-PLOT twist."))
                .exchange().expectStatus().isCreated()).path("id").asText();
        owner.client().put().uri("/api/scenes/" + scene + "/shoot-dates")
                .bodyValue(Map.of("shootDateStart", "2026-11-02", "shootDateEnd", "2026-11-03", "callTime", "17:00", "wrapTime", "02:00"))
                .exchange().expectStatus().isOk();
        jdbc.update("UPDATE scenes SET parse_status = 'PARSED', setting_type = 'diner', estimated_crew_size = 25 WHERE id = ?::uuid", scene);
        String venue = json(owner.client().post().uri("/api/scenes/" + scene + "/locations")
                .bodyValue(Map.of("name", "Starlite Diner", "address", "12 Example Ave, Brooklyn", "notes", "PRIVATE-NOTE owner is grumpy"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        owner.client().put().uri("/api/locations/" + venue + "/contact")
                .bodyValue(Map.of("name", "Rita Moss", "email", "rita@starlite.example", "phone", "+1 718 555 0100", "quote", "1,200 a day"))
                .exchange().expectStatus().isOk();
        return new String[] {project, venue};
    }

    private JsonNode generate(Account who, String venue, Object body) {
        return json(who.client().post().uri("/api/locations/" + venue + "/agreements").bodyValue(body).exchange().expectStatus().isCreated());
    }

    private EntityExchangeResult<byte[]> download(Account who, String agreementId) {
        return who.client().get().uri("/api/agreements/" + agreementId + "/file").exchange().expectStatus().isOk()
                .expectBody(byte[].class).returnResult();
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private void addMember(Account owner, String projectId, Account who, String role) {
        owner.client().post().uri("/api/projects/" + projectId + "/members").bodyValue(Map.of("email", who.email(), "role", role))
                .exchange().expectStatus().is2xxSuccessful();
    }

    @Test
    void aReleaseIsFilledInFromTheVenueAndSceneAndNothingPrivate() throws IOException {
        Account ada = register("Ada");
        String venue = venue(ada)[1];

        JsonNode created = generate(ada, venue, Map.of("productionCompany", "Harbour Films Ltd"));
        assertThat(created.path("version").asInt()).isEqualTo(1);
        assertThat(created.path("createdByName").asText()).isEqualTo("Ada");

        EntityExchangeResult<byte[]> file = download(ada, created.path("id").asText());
        assertThat(file.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(file.getResponseHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"location-release-starlite-diner-v1.pdf\"");
        String text = text(file.getResponseBody());
        assertThat(text).contains("The Night Ferry", "Harbour Films Ltd", "Starlite Diner", "12 Example Ave, Brooklyn", "Rita Moss",
                "rita@starlite.example", "1,200 a day", "Monday 2 November 2026 to Tuesday 3 November 2026",
                "from 17:00 to 02:00 (the next morning)", "About 25", "Template only — not legal advice. Have it reviewed before signing.");
        assertThat(text).doesNotContain("PRIVATE-NOTE", "SECRET-PLOT");
    }

    @Test
    void regeneratingMakesANewVersionAndOldOnesCanBeDeleted() {
        Account ada = register("Ada");
        String venue = venue(ada)[1];
        String first = generate(ada, venue, Map.of()).path("id").asText();
        String second = generate(ada, venue, Map.of()).path("id").asText();

        JsonNode list = json(ada.client().get().uri("/api/locations/" + venue + "/agreements").exchange().expectStatus().isOk());
        assertThat(list.path("items").findValuesAsText("id")).containsExactly(second, first);
        assertThat(list.path("items").findValuesAsText("version")).containsExactly("2", "1");

        ada.client().delete().uri("/api/agreements/" + first).exchange().expectStatus().isNoContent();
        ada.client().get().uri("/api/agreements/" + first + "/file").exchange().expectStatus().isNotFound();
        assertThat(generate(ada, venue, Map.of()).path("version").asInt()).isEqualTo(3);
    }

    @Test
    void aVenueKeepsAtMostThirtyVersions() {
        Account ada = register("Ada");
        String venue = venue(ada)[1];
        jdbc.update("""
                INSERT INTO location_agreements (location_id, version, storage_key, size_bytes)
                SELECT ?::uuid, v, 'agreements/old/' || v || '.pdf', 100 FROM generate_series(1, 30) v""", venue);

        JsonNode problem = json(ada.client().post().uri("/api/locations/" + venue + "/agreements").bodyValue(Map.of())
                .exchange().expectStatus().isEqualTo(409));
        assertThat(problem.path("detail").asText()).contains("delete an old one first");
    }

    @Test
    void viewersDownloadButOnlyEditorsMakeOrDeleteAndOutsidersSeeNothing() {
        Account ada = register("Ada");
        Account vera = register("Vera");
        Account mallory = register("Mallory");
        String[] ids = venue(ada);
        addMember(ada, ids[0], vera, "VIEWER");
        String agreement = generate(ada, ids[1], Map.of()).path("id").asText();

        vera.client().get().uri("/api/locations/" + ids[1] + "/agreements").exchange().expectStatus().isOk();
        download(vera, agreement);
        vera.client().post().uri("/api/locations/" + ids[1] + "/agreements").bodyValue(Map.of()).exchange().expectStatus().isForbidden();
        vera.client().delete().uri("/api/agreements/" + agreement).exchange().expectStatus().isForbidden();

        mallory.client().get().uri("/api/locations/" + ids[1] + "/agreements").exchange().expectStatus().isNotFound();
        mallory.client().get().uri("/api/agreements/" + agreement + "/file").exchange().expectStatus().isNotFound();
        mallory.client().post().uri("/api/locations/" + ids[1] + "/agreements").bodyValue(Map.of()).exchange().expectStatus().isNotFound();
        mallory.client().delete().uri("/api/agreements/" + agreement).exchange().expectStatus().isNotFound();
        web.get().uri("/api/agreements/" + agreement + "/file").exchange().expectStatus().isUnauthorized();
    }
}
