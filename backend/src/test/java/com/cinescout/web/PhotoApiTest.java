package com.cinescout.web;

import com.cinescout.photos.TestPhotos;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import org.springframework.web.reactive.function.BodyInserters;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Recce photos through the whole application: upload, links, cover, limits and who may do what. */
class PhotoApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Night Shift", "locationArea", "London"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String venue(Account member, String projectId) {
        String scene = json(member.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("title", "Pub", "sourceText", "INT. PUB - NIGHT")).exchange().expectStatus().isCreated()).path("id").asText();
        return json(member.client().post().uri("/api/scenes/" + scene + "/locations").bodyValue(Map.of("name", "The Lamb"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private ResponseSpec upload(Account member, String venue, byte[] bytes, String filename, Map<String, String> fields) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.IMAGE_JPEG);
        fields.forEach(body::part);
        return member.client().post().uri("/api/locations/" + venue + "/photos")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .exchange();
    }

    private void add(Account owner, String projectId, Account who, String role) {
        owner.client().post().uri("/api/projects/" + projectId + "/members").bodyValue(Map.of("email", who.email(), "role", role))
                .exchange().expectStatus().isCreated();
    }

    @Test
    void aPhotoIsKeptWithoutItsMetadataSuggestsThePinAndIsServedOnlyBySignedLink() {
        Account ada = register("Ada");
        String venue = venue(ada, project(ada));
        byte[] phone = TestPhotos.withExif(TestPhotos.jpeg(400, 300), 1, 51.5072, -0.1276);

        JsonNode uploaded = json(upload(ada, venue, phone, "IMG_0001.jpg", Map.of()).expectStatus().isCreated());
        assertThat(uploaded.path("suggestPin").asBoolean()).isTrue();
        JsonNode photo = uploaded.path("photo");
        assertThat(photo.path("latitude").asDouble()).isEqualTo(51.5072);
        assertThat(photo.path("longitude").asDouble()).isEqualTo(-0.1276);
        assertThat(photo.path("uploadedBy").asText()).isEqualTo("Ada");
        assertThat(photo.path("width").asInt()).isEqualTo(400);

        byte[] served = web.get().uri(photo.path("url").asText()).exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_JPEG)
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectBody(byte[].class).returnResult().getResponseBody();
        assertThat(new String(served, StandardCharsets.ISO_8859_1)).doesNotContain("Exif", "SECRET-OWNER");
        web.get().uri(photo.path("thumbUrl").asText()).exchange().expectStatus().isOk();
        String tampered = photo.path("url").asText().replace("size=full", "size=thumb");
        web.get().uri(tampered).exchange().expectStatus().isNotFound();
        web.get().uri("/api/public/photos/" + photo.path("id").asText() + "?size=full&exp=9999999999&sig=forged").exchange()
                .expectStatus().isNotFound();

        assertThat(json(ada.client().get().uri("/api/locations/" + venue + "/photos").exchange().expectStatus().isOk())
                .path("totalItems").asInt()).isEqualTo(1);
    }

    @Test
    void theBrowsersReadingOfWhereAConvertedPhotoWasTakenIsUsedOnlyWhenThePhotoSaysNothing() {
        Account ada = register("Ada");
        String venue = venue(ada, project(ada));
        ada.client().put().uri("/api/locations/" + venue + "/coordinates").bodyValue(Map.of("latitude", 51.5, "longitude", -0.1))
                .exchange().expectStatus().isOk();

        JsonNode converted = json(upload(ada, venue, TestPhotos.jpeg(80, 60), "IMG_0002.jpg",
                Map.of("latitude", "40.7128", "longitude", "-74.006")).expectStatus().isCreated());
        assertThat(converted.path("photo").path("latitude").asDouble()).isEqualTo(40.7128);
        assertThat(converted.path("suggestPin").asBoolean()).isFalse();

        JsonNode both = json(upload(ada, venue, TestPhotos.withExif(TestPhotos.jpeg(80, 60), 1, 51.5072, -0.1276), "x.jpg",
                Map.of("latitude", "40.7128", "longitude", "-74.006")).expectStatus().isCreated());
        assertThat(both.path("photo").path("latitude").asDouble()).isEqualTo(51.5072);
    }

    @Test
    void aChosenPhotoBecomesTheVenuesPictureUntilItIsDeleted() {
        Account ada = register("Ada");
        String venue = venue(ada, project(ada));
        jdbc.update("UPDATE locations SET image_url = 'https://venue.example/og.jpg' WHERE id = ?::uuid", venue);
        String photo = json(upload(ada, venue, TestPhotos.jpeg(80, 60), "a.jpg", Map.of()).expectStatus().isCreated())
                .path("photo").path("id").asText();

        JsonNode covered = json(ada.client().put().uri("/api/locations/" + venue + "/cover").bodyValue(Map.of("photoId", photo))
                .exchange().expectStatus().isOk());
        assertThat(covered.path("coverPhotoId").asText()).isEqualTo(photo);
        assertThat(covered.path("imageUrl").asText()).startsWith("/api/public/photos/" + photo + "?size=full&exp=");
        web.get().uri(covered.path("imageUrl").asText()).exchange().expectStatus().isOk();
        assertThat(json(ada.client().get().uri("/api/locations/" + venue + "/photos").exchange()).path("items").get(0)
                .path("cover").asBoolean()).isTrue();

        ada.client().put().uri("/api/locations/" + venue + "/cover").bodyValue(Map.of("photoId", UUID.randomUUID().toString()))
                .exchange().expectStatus().isBadRequest();
        ada.client().delete().uri("/api/photos/" + photo).exchange().expectStatus().isNoContent();
        JsonNode after = json(ada.client().get().uri("/api/locations/" + venue).exchange().expectStatus().isOk());
        assertThat(after.path("coverPhotoId").isNull()).isTrue();
        assertThat(after.path("imageUrl").asText()).isEqualTo("https://venue.example/og.jpg");
        web.get().uri(covered.path("imageUrl").asText()).exchange().expectStatus().isNotFound();
    }

    @Test
    void onlyPhotosAreTakenUpToThirtyAVenueAndTenMegabytesEach() {
        Account ada = register("Ada");
        String venue = venue(ada, project(ada));

        upload(ada, venue, "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8), "x.svg", Map.of()).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("file");
        upload(ada, venue, new byte[10 * 1024 * 1024 + 1], "huge.jpg", Map.of()).expectStatus().isEqualTo(413);
        for (int i = 0; i < 30; i++) {
            jdbc.update("INSERT INTO location_photos (location_id, storage_key, thumb_key, content_type, width, height, size_bytes) "
                    + "VALUES (?::uuid, 'photos/x/y.jpg', 'photos/x/y-thumb.jpg', 'image/jpeg', 1, 1, 1)", venue);
        }
        upload(ada, venue, TestPhotos.jpeg(80, 60), "31.jpg", Map.of()).expectStatus().isEqualTo(409);
    }

    @Test
    void viewersSeeThePhotosButAddNoneAndOutsidersSeeNothing() {
        Account ada = register("Ada");
        Account vera = register("Vera");
        Account outsider = register("Mallory");
        String project = project(ada);
        add(ada, project, vera, "VIEWER");
        String venue = venue(ada, project);
        String photo = json(upload(ada, venue, TestPhotos.jpeg(80, 60), "a.jpg", Map.of()).expectStatus().isCreated())
                .path("photo").path("id").asText();

        vera.client().get().uri("/api/locations/" + venue + "/photos").exchange().expectStatus().isOk();
        upload(vera, venue, TestPhotos.jpeg(80, 60), "b.jpg", Map.of()).expectStatus().isForbidden();
        vera.client().delete().uri("/api/photos/" + photo).exchange().expectStatus().isForbidden();
        vera.client().put().uri("/api/locations/" + venue + "/cover").bodyValue(Map.of("photoId", photo)).exchange().expectStatus().isForbidden();

        outsider.client().get().uri("/api/locations/" + venue + "/photos").exchange().expectStatus().isNotFound();
        upload(outsider, venue, TestPhotos.jpeg(80, 60), "c.jpg", Map.of()).expectStatus().isNotFound();
        outsider.client().delete().uri("/api/photos/" + photo).exchange().expectStatus().isNotFound();
    }
}
