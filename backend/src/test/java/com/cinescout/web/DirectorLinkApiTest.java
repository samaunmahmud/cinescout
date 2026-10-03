package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Director links, through the whole application: what a guest sees, what they may answer, and who may share. */
class DirectorLinkApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Night Shift", "locationArea", "Brooklyn"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String scene(Account member, String projectId, String title) {
        return json(member.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("title", title, "sourceText", "PRIVATE-SCRIPT"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    /** A venue in {@code status}, with a private note, a quote, contact details and a fit reason on it. */
    private String venue(Account member, String sceneId, String name, String status) {
        String id = json(member.client().post().uri("/api/scenes/" + sceneId + "/locations")
                .bodyValue(Map.of("name", name, "latitude", 40.7, "longitude", -73.9))
                .exchange().expectStatus().isCreated()).path("id").asText();
        member.client().put().uri("/api/locations/" + id).bodyValue(Map.of("status", status, "notes", "PRIVATE-NOTE"))
                .exchange().expectStatus().isOk();
        member.client().put().uri("/api/locations/" + id + "/contact")
                .bodyValue(Map.of("name", "PRIVATE-CONTACT", "email", "owner@example.com", "quote", "PRIVATE-QUOTE"))
                .exchange().expectStatus().isOk();
        jdbc.update("UPDATE locations SET fit_score = 72, fit_reason = 'PRIVATE-REASON', friction_note = 'Book two weeks ahead', "
                + "footprint_warnings = '[\"Busy street\"]'::jsonb WHERE id = ?::uuid", id);
        return id;
    }

    private String share(Account member, String uri, Map<String, Object> body) {
        return json(member.client().post().uri(uri).bodyValue(body).exchange().expectStatus().isOk()).path("token").asText();
    }

    private void add(Account owner, String projectId, Account who, String role) {
        owner.client().post().uri("/api/projects/" + projectId + "/members").bodyValue(Map.of("email", who.email(), "role", role))
                .exchange().expectStatus().isCreated();
    }

    private ResponseSpec answer(String token, String venue, String name, String verdict, String comment) {
        Map<String, Object> body = comment == null ? Map.of("guestName", name, "verdict", verdict)
                : Map.of("guestName", name, "verdict", verdict, "comment", comment);
        return web.post().uri("/api/public/shortlists/" + token + "/venues/" + venue + "/response").bodyValue(body).exchange();
    }

    @Test
    void aGuestSeesTheShortlistWithoutAnAccountAndNothingPrivate() {
        Account ada = register("Ada");
        String project = project(ada);
        String diner = scene(ada, project, "Diner");
        String shortlisted = venue(ada, diner, "Moonlight Diner", "SHORTLISTED");
        venue(ada, diner, "Suggested Cafe", "SUGGESTED");
        venue(ada, diner, "Rejected Bar", "REJECTED");
        String confirmed = venue(ada, scene(ada, project, "Rooftop"), "Sky Roof", "CONFIRMED");
        ada.client().get().uri("/api/projects/" + project + "/director-link").exchange().expectStatus().isNotFound();

        String token = share(ada, "/api/projects/" + project + "/director-link", Map.of());
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(json(ada.client().get().uri("/api/projects/" + project + "/director-link").exchange().expectStatus().isOk())
                .path("token").asText()).isEqualTo(token);

        String body = web.get().uri("/api/public/shortlists/" + token).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Robots-Tag", "noindex, nofollow")
                .expectHeader().cacheControl(org.springframework.http.CacheControl.noStore())
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(body).doesNotContain("PRIVATE-NOTE", "PRIVATE-QUOTE", "PRIVATE-REASON", "PRIVATE-CONTACT", "owner@example.com",
                "PRIVATE-SCRIPT", "Suggested Cafe", "Rejected Bar");

        JsonNode shortlist = json(web.get().uri("/api/public/shortlists/" + token).exchange());
        assertThat(shortlist.path("projectTitle").asText()).isEqualTo("Night Shift");
        assertThat(shortlist.path("sharedBy").asText()).isEqualTo("Ada");
        assertThat(shortlist.path("showPrivate").asBoolean()).isFalse();
        JsonNode venues = shortlist.path("venues").path("items");
        assertThat(venues).hasSize(2);
        assertThat(venues.get(0).path("id").asText()).isEqualTo(shortlisted);
        assertThat(venues.get(0).path("sceneTitle").asText()).isEqualTo("Diner");
        assertThat(venues.get(0).path("fitScore").asInt()).isEqualTo(72);
        assertThat(venues.get(0).path("frictionNote").asText()).isEqualTo("Book two weeks ahead");
        assertThat(venues.get(0).path("warnings").get(0).asText()).isEqualTo("Busy street");
        assertThat(venues.get(0).path("latitude").asDouble()).isEqualTo(40.7);
        assertThat(venues.get(1).path("id").asText()).isEqualTo(confirmed);
    }

    @Test
    void onlyTheOwnerCanShowPrivateDetailsAndThenTheGuestSeesThem() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String project = project(ada);
        add(ada, project, grace, "EDITOR");
        String scene = scene(ada, project, "Diner");
        venue(ada, scene, "Moonlight Diner", "SHORTLISTED");

        grace.client().post().uri("/api/projects/" + project + "/director-link").bodyValue(Map.of("showPrivate", true))
                .exchange().expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        share(grace, "/api/projects/" + project + "/director-link", Map.of("showPrivate", false));

        String token = share(ada, "/api/projects/" + project + "/director-link", Map.of("showPrivate", true));
        JsonNode venue = json(web.get().uri("/api/public/shortlists/" + token).exchange()).path("venues").path("items").get(0);
        assertThat(venue.path("notes").asText()).isEqualTo("PRIVATE-NOTE");
        assertThat(venue.path("quote").asText()).isEqualTo("PRIVATE-QUOTE");
        assertThat(venue.path("fitReason").asText()).isEqualTo("PRIVATE-REASON");
        assertThat(venue.has("contactEmail")).isFalse();
    }

    @Test
    void aGuestAnswersEachVenueOncePerNameAndTheCrewSeesTheirCalls() {
        Account ada = register("Ada");
        String project = project(ada);
        String scene = scene(ada, project, "Diner");
        String venue = venue(ada, scene, "Moonlight Diner", "SHORTLISTED");
        String token = share(ada, "/api/scenes/" + scene + "/director-link", Map.of());

        JsonNode first = json(answer(token, venue, " Wes ", "APPROVE", "Love the booths").expectStatus().isOk());
        assertThat(first.path("guestName").asText()).isEqualTo("Wes");
        assertThat(first.path("verdict").asText()).isEqualTo("APPROVE");
        answer(token, venue, "wes", "MAYBE", "  ").expectStatus().isOk();
        answer(token, venue, "Sofia", "NO", "Too bright").expectStatus().isOk();

        JsonNode calls = json(ada.client().get().uri("/api/locations/" + venue + "/director-responses").exchange().expectStatus().isOk());
        assertThat(calls.path("totalItems").asInt()).isEqualTo(2);
        assertThat(calls.path("items").get(0).path("guestName").asText()).isEqualTo("Sofia");
        JsonNode wes = calls.path("items").get(1);
        assertThat(wes.path("guestName").asText()).isEqualTo("wes");
        assertThat(wes.path("verdict").asText()).isEqualTo("MAYBE");
        assertThat(wes.path("comment").isNull()).isTrue();

        assertThat(json(ada.client().get().uri("/api/scenes/" + scene + "/director-responses").exchange().expectStatus().isOk())
                .path("items").get(0).path("locationId").asText()).isEqualTo(venue);
        assertThat(json(web.get().uri("/api/public/shortlists/" + token).exchange())
                .path("venues").path("items").get(0).path("responses")).hasSize(2);

        answer(token, venue, " ", "APPROVE", null).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("guestName");
        web.post().uri("/api/public/shortlists/" + token + "/venues/" + venue + "/response")
                .bodyValue(Map.of("guestName", "Wes", "verdict", "LOVE IT")).exchange().expectStatus().isBadRequest();
    }

    @Test
    void aSceneLinkShowsAndTakesAnswersForThatSceneOnly() {
        Account ada = register("Ada");
        String project = project(ada);
        String diner = scene(ada, project, "Diner");
        String rooftop = scene(ada, project, "Rooftop");
        String dinerVenue = venue(ada, diner, "Moonlight Diner", "SHORTLISTED");
        String rooftopVenue = venue(ada, rooftop, "Sky Roof", "SHORTLISTED");
        String suggested = venue(ada, diner, "Suggested Cafe", "SUGGESTED");
        String elsewhere = venue(ada, scene(ada, project(ada), "Other"), "Elsewhere", "SHORTLISTED");
        String token = share(ada, "/api/scenes/" + diner + "/director-link", Map.of());

        JsonNode shortlist = json(web.get().uri("/api/public/shortlists/" + token).exchange());
        assertThat(shortlist.path("sceneTitle").asText()).isEqualTo("Diner");
        assertThat(shortlist.path("venues").path("items")).hasSize(1);
        answer(token, dinerVenue, "Wes", "APPROVE", null).expectStatus().isOk();
        for (String other : new String[] {rooftopVenue, suggested, elsewhere, UUID.randomUUID().toString()}) {
            answer(token, other, "Wes", "APPROVE", null).expectStatus().isNotFound();
        }
        assertThat(json(ada.client().get().uri("/api/projects/" + project + "/director-link").exchange().expectStatus().isNotFound()))
                .isNotNull();
    }

    @Test
    void aNewLinkReplacesTheOldAndWithdrawingItKeepsTheCalls() {
        Account ada = register("Ada");
        String project = project(ada);
        String venue = venue(ada, scene(ada, project, "Diner"), "Moonlight Diner", "SHORTLISTED");
        String old = share(ada, "/api/projects/" + project + "/director-link", Map.of());
        answer(old, venue, "Wes", "APPROVE", null).expectStatus().isOk();

        String fresh = share(ada, "/api/projects/" + project + "/director-link", Map.of());
        assertThat(fresh).isNotEqualTo(old);
        web.get().uri("/api/public/shortlists/" + old).exchange().expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        answer(old, venue, "Wes", "NO", null).expectStatus().isNotFound();

        ada.client().delete().uri("/api/projects/" + project + "/director-link").exchange().expectStatus().isNoContent();
        ada.client().delete().uri("/api/projects/" + project + "/director-link").exchange().expectStatus().isNoContent();
        web.get().uri("/api/public/shortlists/" + fresh).exchange().expectStatus().isNotFound();
        web.get().uri("/api/public/shortlists/not-a-token").exchange().expectStatus().isNotFound();
        assertThat(json(ada.client().get().uri("/api/locations/" + venue + "/director-responses").exchange())
                .path("items").get(0).path("verdict").asText()).isEqualTo("APPROVE");
    }

    @Test
    void someoneNotOnTheCrewSeesNoLinkAndAViewerCannotShare() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        Account hedy = register("Hedy");
        String project = project(ada);
        add(ada, project, hedy, "VIEWER");
        String scene = scene(ada, project, "Diner");
        String venue = venue(ada, scene, "Moonlight Diner", "SHORTLISTED");
        share(ada, "/api/projects/" + project + "/director-link", Map.of());
        share(ada, "/api/scenes/" + scene + "/director-link", Map.of());

        for (String uri : new String[] {"/api/projects/" + project + "/director-link", "/api/scenes/" + scene + "/director-link",
                "/api/locations/" + venue + "/director-responses", "/api/scenes/" + scene + "/director-responses"}) {
            grace.client().get().uri(uri).exchange().expectStatus().isNotFound();
            hedy.client().get().uri(uri).exchange().expectStatus().isOk();
        }
        grace.client().post().uri("/api/projects/" + project + "/director-link").exchange().expectStatus().isNotFound();
        grace.client().delete().uri("/api/scenes/" + scene + "/director-link").exchange().expectStatus().isNotFound();
        hedy.client().post().uri("/api/projects/" + project + "/director-link").exchange().expectStatus().isForbidden();
        hedy.client().delete().uri("/api/scenes/" + scene + "/director-link").exchange().expectStatus().isForbidden();
        web.get().uri("/api/projects/" + project + "/director-link").exchange().expectStatus().isUnauthorized();
    }
}
