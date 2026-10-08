package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A scene's shot list end to end: order, the sun on each shot at its venue, and who may change it. */
class ShotApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String[] sceneWithConfirmedVenue(Account owner) {
        String project = json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Night Shift")).exchange().expectStatus().isCreated())
                .path("id").asText();
        String scene = json(owner.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("title", "Rooftop", "sourceText", "EXT. ROOFTOP - DAY")).exchange().expectStatus().isCreated()).path("id").asText();
        owner.client().put().uri("/api/scenes/" + scene + "/shoot-dates").bodyValue(Map.of("shootDateStart", "2026-06-21")).exchange().expectStatus().isOk();
        String venue = json(owner.client().post().uri("/api/scenes/" + scene + "/locations")
                .bodyValue(Map.of("name", "Skyline Terrace", "latitude", 51.5074, "longitude", -0.1278)).exchange().expectStatus().isCreated())
                .path("id").asText();
        owner.client().put().uri("/api/locations/" + venue).bodyValue(Map.of("status", "CONFIRMED")).exchange().expectStatus().isOk();
        return new String[] {project, scene, venue};
    }

    private static Map<String, Object> shot(String description, Integer bearing, String time) {
        Map<String, Object> shot = new HashMap<>(Map.of("description", description, "size", "WIDE"));
        if (bearing != null) {
            shot.put("cameraBearing", bearing);
        }
        if (time != null) {
            shot.put("plannedTime", time);
        }
        return shot;
    }

    private JsonNode add(Account who, String scene, Map<String, Object> shot) {
        return json(who.client().post().uri("/api/scenes/" + scene + "/shots").bodyValue(shot).exchange().expectStatus().isCreated());
    }

    @Test
    void eachShotSaysHowTheSunLightsItOnceTheVenuesTimeZoneIsKnown() {
        Account ada = register("Ada");
        String[] ids = sceneWithConfirmedVenue(ada);
        String scene = ids[1];

        JsonNode list = add(ada, scene, shot("Wide over the skyline, facing the sun", 180, "13:00"));
        assertThat(list.get(0).path("number").asInt()).isEqualTo(1);
        assertThat(list.get(0).path("venueName").asText()).isEqualTo("Skyline Terrace");
        assertThat(list.get(0).path("venueConfirmed").asBoolean()).isTrue();
        assertThat(list.get(0).path("sunMissing").asText()).isEqualTo("NO_ZONE");

        jdbc.update("UPDATE locations SET logistics_json = '{\"timeZone\":\"Europe/London\"}'::jsonb WHERE id = ?::uuid", ids[2]);
        add(ada, scene, shot("Reverse on the actors", 0, "13:00"));
        add(ada, scene, shot("Profile, looking east", 90, "13:00"));
        add(ada, scene, shot("Night pick-up", 0, "23:30"));
        list = add(ada, scene, shot("Insert of the note", null, null));

        assertThat(list.get(0).path("sun").path("light").asText()).isEqualTo("BACKLIT");
        assertThat(list.get(0).path("sun").path("sun").path("compass").asText()).isEqualTo("S");
        assertThat(list.get(0).path("sun").path("sun").path("elevation").asDouble()).isGreaterThan(55);
        assertThat(list.get(1).path("sun").path("light").asText()).isEqualTo("FRONT_LIT");
        assertThat(list.get(2).path("sun").path("light").asText()).isEqualTo("SIDE_LIT");
        assertThat(list.get(3).path("sun").path("light").asText()).isEqualTo("SUN_DOWN");
        assertThat(list.get(4).path("sunMissing").asText()).isEqualTo("NO_TIME");
        assertThat(list.findValuesAsText("description").stream().filter(d -> !d.isBlank()).count()).isEqualTo(5);
    }

    @Test
    void shotsMoveEditAndGoAndOnlyEditorsChangeThem() {
        Account ada = register("Ada");
        Account vic = register("Vic");
        Account mal = register("Mal");
        String[] ids = sceneWithConfirmedVenue(ada);
        String scene = ids[1];
        add(ada, scene, shot("One", null, null));
        add(ada, scene, shot("Two", null, null));
        JsonNode list = add(ada, scene, shot("Three", null, null));
        String three = list.get(2).path("id").asText();

        list = json(ada.client().put().uri("/api/shots/" + three + "/position").bodyValue(Map.of("direction", "EARLIER")).exchange().expectStatus().isOk());
        assertThat(list.findValuesAsText("description")).containsExactly("One", "Three", "Two");
        list = json(ada.client().put().uri("/api/shots/" + list.get(0).path("id").asText() + "/position").bodyValue(Map.of("direction", "EARLIER"))
                .exchange().expectStatus().isOk());
        assertThat(list.findValuesAsText("description")).containsExactly("One", "Three", "Two"); // already first

        Map<String, Object> done = shot("Three, wider", 270, "09:15");
        done.put("done", true);
        list = json(ada.client().put().uri("/api/shots/" + three).bodyValue(done).exchange().expectStatus().isOk());
        assertThat(list.get(1).path("done").asBoolean()).isTrue();
        assertThat(list.get(1).path("cameraBearing").asInt()).isEqualTo(270);
        assertThat(list.get(1).path("plannedTime").asText()).isEqualTo("09:15:00");

        list = json(ada.client().delete().uri("/api/shots/" + three).exchange().expectStatus().isOk());
        assertThat(list.findValuesAsText("description")).containsExactly("One", "Two");
        assertThat(list.get(1).path("number").asInt()).isEqualTo(2);

        ada.client().post().uri("/api/projects/" + ids[0] + "/members").bodyValue(Map.of("email", vic.email(), "role", "VIEWER"))
                .exchange().expectStatus().is2xxSuccessful();
        vic.client().get().uri("/api/scenes/" + scene + "/shots").exchange().expectStatus().isOk();
        vic.client().post().uri("/api/scenes/" + scene + "/shots").bodyValue(shot("x", null, null)).exchange().expectStatus().isForbidden();
        mal.client().get().uri("/api/scenes/" + scene + "/shots").exchange().expectStatus().isNotFound();
        ada.client().post().uri("/api/scenes/" + scene + "/shots").bodyValue(shot("Bad bearing", 360, null)).exchange().expectStatus().isBadRequest();
    }
}
