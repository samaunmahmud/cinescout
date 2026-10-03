package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The location library, through the whole application: saving, finding, tagging and reusing venues. */
class LibraryApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Night Shift", "locationArea", "Brooklyn"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String scene(Account member, String projectId) {
        return json(member.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("title", "Diner", "sourceText", "INT. DINER - NIGHT"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    /** A scouted-looking venue: picture, friction, warnings, a contact, a fit score, a status and crew notes. */
    private String venue(Account member, String sceneId, String name, String url) {
        String id = json(member.client().post().uri("/api/scenes/" + sceneId + "/locations")
                .bodyValue(Map.of("name", name, "address", "12 Main St", "latitude", 40.7, "longitude", -73.9, "sourceUrl", url))
                .exchange().expectStatus().isCreated()).path("id").asText();
        member.client().put().uri("/api/locations/" + id + "/contact").bodyValue(Map.of("name", "Sal", "phone", "555 0100"))
                .exchange().expectStatus().isOk();
        member.client().put().uri("/api/locations/" + id).bodyValue(Map.of("status", "SHORTLISTED", "notes", "CREW-NOTE"))
                .exchange().expectStatus().isOk();
        jdbc.update("UPDATE locations SET fit_score = 80, fit_reason = 'Booths', booking_friction = 'COMMERCIAL', "
                + "friction_note = 'Ask the manager', footprint_warnings = '[\"No lift\"]'::jsonb, image_url = 'https://cdn.example/d.jpg' "
                + "WHERE id = ?::uuid", id);
        return id;
    }

    private static List<String> names(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.path("items").forEach(item -> names.add(item.path("name").asText()));
        return names;
    }

    @Test
    void aVenueIsSavedOnceWithWhatHoldsWhateverTheSceneAndCanBeTaggedAndFound() {
        Account ada = register("Ada");
        String scene = scene(ada, project(ada));
        String diner = venue(ada, scene, "Moonlight Diner", "https://diner.example/");
        String bar = venue(ada, scene, "Neon Bar", "https://bar.example/");

        JsonNode saved = json(ada.client().post().uri("/api/library").bodyValue(Map.of("locationId", diner)).exchange().expectStatus().isCreated());
        assertThat(saved.path("name").asText()).isEqualTo("Moonlight Diner");
        assertThat(saved.path("imageUrl").asText()).isEqualTo("https://cdn.example/d.jpg");
        assertThat(saved.path("bookingFriction").asText()).isEqualTo("COMMERCIAL");
        assertThat(saved.path("footprintWarnings").get(0).asText()).isEqualTo("No lift");
        assertThat(saved.path("contactName").asText()).isEqualTo("Sal");
        assertThat(saved.path("sourceLocationId").asText()).isEqualTo(diner);
        assertThat(saved.has("fitScore")).isFalse();
        assertThat(saved.toString()).doesNotContain("CREW-NOTE");
        String id = saved.path("id").asText();
        assertThat(json(ada.client().post().uri("/api/library").bodyValue(Map.of("locationId", diner)).exchange().expectStatus().isOk())
                .path("id").asText()).isEqualTo(id);
        String barId = json(ada.client().post().uri("/api/library").bodyValue(Map.of("locationId", bar)).exchange().expectStatus().isCreated())
                .path("id").asText();

        JsonNode tagged = json(ada.client().put().uri("/api/library/" + id)
                .bodyValue(Map.of("name", " Moonlight ", "tags", List.of("Diner", " night ", "diner"), "notes", "Owner loves film crews"))
                .exchange().expectStatus().isOk());
        assertThat(tagged.path("tags")).hasSize(2);
        ada.client().put().uri("/api/library/" + barId).bodyValue(Map.of("name", "Neon Bar", "tags", List.of("Night")))
                .exchange().expectStatus().isOk();

        assertThat(names(json(ada.client().get().uri("/api/library").exchange().expectStatus().isOk()))).containsExactly("Neon Bar", "Moonlight");
        assertThat(names(json(ada.client().get().uri("/api/library?q=FILM").exchange()))).containsExactly("Moonlight");
        assertThat(names(json(ada.client().get().uri("/api/library?q=100%25").exchange()))).isEmpty();
        assertThat(names(json(ada.client().get().uri("/api/library?tag=NIGHT").exchange()))).containsExactly("Neon Bar", "Moonlight");
        assertThat(names(json(ada.client().get().uri("/api/library?tag=diner").exchange()))).containsExactly("Moonlight");
        JsonNode tags = json(ada.client().get().uri("/api/library/tags").exchange().expectStatus().isOk());
        assertThat(tags.get(0).path("venues").asInt()).isEqualTo(2);
        assertThat(tags).hasSize(2);
    }

    @Test
    void aLibraryVenueIsCopiedIntoAnySceneWithoutItsLibraryNotesOrAScore() {
        Account ada = register("Ada");
        String first = scene(ada, project(ada));
        String diner = venue(ada, first, "Moonlight Diner", "https://diner.example/");
        String id = json(ada.client().post().uri("/api/library").bodyValue(Map.of("locationId", diner)).exchange()).path("id").asText();
        ada.client().put().uri("/api/library/" + id).bodyValue(Map.of("name", "Moonlight Diner", "notes", "LIBRARY-NOTE")).exchange();
        String other = scene(ada, project(ada));

        JsonNode copy = json(ada.client().post().uri("/api/scenes/" + other + "/locations/from-library")
                .bodyValue(Map.of("libraryVenueId", id)).exchange().expectStatus().isCreated());
        assertThat(copy.path("sceneId").asText()).isEqualTo(other);
        assertThat(copy.path("name").asText()).isEqualTo("Moonlight Diner");
        assertThat(copy.path("status").asText()).isEqualTo("SUGGESTED");
        assertThat(copy.path("fitScore").isNull()).isTrue();
        assertThat(copy.path("sourceProvider").asText()).isEqualTo("library");
        assertThat(copy.path("imageUrl").asText()).isEqualTo("https://cdn.example/d.jpg");
        assertThat(copy.path("imageCheckedAt").isNull()).isFalse();
        assertThat(copy.path("contactPhone").asText()).isEqualTo("555 0100");
        assertThat(copy.path("notes").isNull()).isTrue();
        assertThat(copy.path("latitude").asDouble()).isEqualTo(40.7);

        ada.client().post().uri("/api/scenes/" + other + "/locations/from-library").bodyValue(Map.of("libraryVenueId", id))
                .exchange().expectStatus().isEqualTo(409);
        ada.client().delete().uri("/api/library/" + id).exchange().expectStatus().isNoContent();
        ada.client().get().uri("/api/locations/" + copy.path("id").asText()).exchange().expectStatus().isOk();
    }

    @Test
    void aLibraryIsItsOwnersAloneAndCopyingNeedsAnEditor() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String project = project(ada);
        ada.client().post().uri("/api/projects/" + project + "/members").bodyValue(Map.of("email", grace.email(), "role", "VIEWER"))
                .exchange().expectStatus().isCreated();
        String scene = scene(ada, project);
        String diner = venue(ada, scene, "Moonlight Diner", "https://diner.example/");
        String adas = json(ada.client().post().uri("/api/library").bodyValue(Map.of("locationId", diner)).exchange()).path("id").asText();

        // A viewer may keep a venue they can see; it is their own copy.
        String graces = json(grace.client().post().uri("/api/library").bodyValue(Map.of("locationId", diner)).exchange().expectStatus().isCreated())
                .path("id").asText();
        assertThat(graces).isNotEqualTo(adas);
        grace.client().get().uri("/api/library/" + adas).exchange().expectStatus().isNotFound();
        grace.client().put().uri("/api/library/" + adas).bodyValue(Map.of("name", "x")).exchange().expectStatus().isNotFound();
        grace.client().delete().uri("/api/library/" + adas).exchange().expectStatus().isNotFound();
        assertThat(names(json(grace.client().get().uri("/api/library").exchange()))).hasSize(1);
        grace.client().post().uri("/api/scenes/" + scene + "/locations/from-library").bodyValue(Map.of("libraryVenueId", graces))
                .exchange().expectStatus().isForbidden();
        ada.client().post().uri("/api/scenes/" + scene + "/locations/from-library").bodyValue(Map.of("libraryVenueId", graces))
                .exchange().expectStatus().isNotFound();

        Account outsider = register("Mallory");
        outsider.client().post().uri("/api/library").bodyValue(Map.of("locationId", diner)).exchange().expectStatus().isNotFound();
        outsider.client().post().uri("/api/library").bodyValue(Map.of("locationId", UUID.randomUUID())).exchange().expectStatus().isNotFound();
    }
}
