package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The project, scene and location endpoints over real HTTP, security and PostgreSQL. */
class ResourceApiTest extends ApiTest {

    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;

    // --- helpers ----------------------------------------------------------------------------------

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private static List<String> errorFields(JsonNode problem) {
        List<String> fields = new ArrayList<>();
        problem.path("errors").forEach(e -> fields.add(e.path("field").asText()));
        return fields;
    }

    /** The ids on a page of a list, in order. */
    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.path("items").forEach(n -> ids.add(n.path("id").asText()));
        return ids;
    }

    private String project(Account owner, String title) {
        return json(owner.client().post().uri("/api/projects")
                .bodyValue(Map.of("title", title, "locationArea", "Brooklyn, New York"))
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String scene(Account owner, String projectId, Integer number, String text) {
        Map<String, Object> body = number == null
                ? Map.of("title", "A scene", "sourceText", text)
                : Map.of("sceneNumber", number, "title", "Scene " + number, "sourceText", text);
        return json(owner.client().post().uri("/api/projects/" + projectId + "/scenes").bodyValue(body)
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    private String location(Account owner, String sceneId, String name, String url) {
        Map<String, Object> body = url == null ? Map.of("name", name) : Map.of("name", name, "sourceUrl", url);
        return json(owner.client().post().uri("/api/scenes/" + sceneId + "/locations").bodyValue(body)
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    // --- projects -------------------------------------------------------------------------------

    @Test
    void creatingAProjectReturns201WithItsLocationAndBody() {
        Account ada = register("Ada");

        ada.client().post().uri("/api/projects")
                .bodyValue(Map.of("title", "Neon Nights", "description", "A neo-noir short", "locationArea", "Brooklyn, New York"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().value("Location", loc -> assertThat(loc).startsWith("/api/projects/"))
                .expectBody()
                .jsonPath("$.title").isEqualTo("Neon Nights")
                .jsonPath("$.locationArea").isEqualTo("Brooklyn, New York")
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.createdAt").isNotEmpty();
    }

    @Test
    void anInvalidProjectListsEveryFieldToFix() {
        JsonNode problem = json(register("Ada").client().post().uri("/api/projects")
                .bodyValue(Map.of("title", " ", "locationArea", "x".repeat(201)))
                .exchange().expectStatus().isBadRequest().expectHeader().contentType(PROBLEM));

        assertThat(errorFields(problem)).containsExactlyInAnyOrder("title", "locationArea");
    }

    @Test
    void projectsListNewestFirstFilterByStatusAndRejectAnUnknownStatus() {
        Account ada = register("Ada");
        String first = project(ada, "First");
        String second = project(ada, "Second");
        ada.client().put().uri("/api/projects/" + first)
                .bodyValue(Map.of("title", "First", "status", "ARCHIVED")).exchange().expectStatus().isOk();

        assertThat(ids(json(ada.client().get().uri("/api/projects").exchange().expectStatus().isOk()))).containsExactly(second, first);
        assertThat(ids(json(ada.client().get().uri("/api/projects?status=ARCHIVED").exchange().expectStatus().isOk()))).containsExactly(first);
        assertThat(ids(json(ada.client().get().uri("/api/projects?status=ACTIVE").exchange().expectStatus().isOk()))).containsExactly(second);
        ada.client().get().uri("/api/projects?status=NOPE").exchange()
                .expectStatus().isBadRequest().expectHeader().contentType(PROBLEM);
    }

    @Test
    void listsComeAPageAtATimeWithTheTotals() {
        Account ada = register("Ada");
        String first = project(ada, "First");
        String second = project(ada, "Second");
        String third = project(ada, "Third");

        JsonNode page0 = json(ada.client().get().uri("/api/projects?size=2").exchange().expectStatus().isOk());
        assertThat(ids(page0)).containsExactly(third, second);
        assertThat(page0.path("page").asInt()).isZero();
        assertThat(page0.path("size").asInt()).isEqualTo(2);
        assertThat(page0.path("totalItems").asLong()).isEqualTo(3);
        assertThat(page0.path("totalPages").asInt()).isEqualTo(2);

        JsonNode page1 = json(ada.client().get().uri("/api/projects?page=1&size=2").exchange().expectStatus().isOk());
        assertThat(ids(page1)).containsExactly(first);
        assertThat(page1.path("page").asInt()).isEqualTo(1);

        JsonNode past = json(ada.client().get().uri("/api/projects?page=7&size=2").exchange().expectStatus().isOk());
        assertThat(ids(past)).isEmpty();
        assertThat(past.path("totalItems").asLong()).isEqualTo(3);

        JsonNode defaults = json(ada.client().get().uri("/api/projects").exchange().expectStatus().isOk());
        assertThat(defaults.path("size").asInt()).isEqualTo(50);
        assertThat(defaults.path("totalPages").asInt()).isEqualTo(1);
    }

    @Test
    void anOutOfRangePageOrSizeIsA400NamingTheParameter() {
        Account ada = register("Ada");
        String project = project(ada, "Neon");

        for (String query : new String[] {"page=-1", "size=0", "size=101", "page=abc"}) {
            JsonNode problem = json(ada.client().get().uri("/api/projects?" + query).exchange()
                    .expectStatus().isBadRequest().expectHeader().contentType(PROBLEM));
            assertThat(errorFields(problem)).as(query).containsExactly(query.substring(0, query.indexOf('=')));
        }
        ada.client().get().uri("/api/projects/" + project + "/scenes?size=101").exchange().expectStatus().isBadRequest();
        ada.client().get().uri("/api/projects?size=100").exchange().expectStatus().isOk();
    }

    @Test
    void updatingAProjectReplacesItSoOmittedOptionalFieldsAreCleared() {
        Account ada = register("Ada");
        String id = project(ada, "Neon Nights");

        ada.client().put().uri("/api/projects/" + id).bodyValue(Map.of("title", "Renamed", "status", "ACTIVE"))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Renamed")
                .jsonPath("$.locationArea").doesNotExist();
        ada.client().put().uri("/api/projects/" + id).bodyValue(Map.of("title", "No status")).exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void deletingAProjectReturns204ThenItIsGoneAlongWithItsScenes() {
        Account ada = register("Ada");
        String id = project(ada, "Doomed");
        String scene = scene(ada, id, 1, "text");

        ada.client().delete().uri("/api/projects/" + id).exchange().expectStatus().isNoContent();

        ada.client().get().uri("/api/projects/" + id).exchange().expectStatus().isNotFound();
        ada.client().get().uri("/api/scenes/" + scene).exchange().expectStatus().isNotFound();
    }

    @Test
    void aMalformedIdIsABadRequestNotAServerError() {
        register("Ada").client().get().uri("/api/projects/not-a-uuid").exchange()
                .expectStatus().isBadRequest().expectHeader().contentType(PROBLEM);
    }

    @Test
    void anotherUsersProjectIsA404ForEveryOperationAndStaysIntact() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String graces = project(grace, "Grace's");

        ada.client().get().uri("/api/projects/" + graces).exchange().expectStatus().isNotFound().expectHeader().contentType(PROBLEM);
        ada.client().put().uri("/api/projects/" + graces).bodyValue(Map.of("title", "Mine", "status", "ACTIVE")).exchange().expectStatus().isNotFound();
        ada.client().delete().uri("/api/projects/" + graces).exchange().expectStatus().isNotFound();
        ada.client().get().uri("/api/projects/" + UUID.randomUUID()).exchange().expectStatus().isNotFound();
        assertThat(ids(json(ada.client().get().uri("/api/projects").exchange().expectStatus().isOk()))).isEmpty();
        grace.client().get().uri("/api/projects/" + graces).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.title").isEqualTo("Grace's");
    }

    // --- scenes ---------------------------------------------------------------------------------

    @Test
    void creatingASceneReturns201AndItStartsPending() {
        Account ada = register("Ada");
        String project = project(ada, "Neon Nights");

        ada.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("sceneNumber", 1, "title", "Rooftop", "sourceText", "INT. ROOFTOP BAR - NIGHT.",
                        "shootDateStart", "2026-10-01", "shootDateEnd", "2026-10-03"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().value("Location", loc -> assertThat(loc).startsWith("/api/scenes/"))
                .expectBody()
                .jsonPath("$.projectId").isEqualTo(project)
                .jsonPath("$.parseStatus").isEqualTo("PENDING")
                .jsonPath("$.requirements").isEmpty()
                .jsonPath("$.shootDateEnd").isEqualTo("2026-10-03");
    }

    @Test
    void sceneValidationNamesTheFieldToFixIncludingTheRuleNamedAfterTheCheck() {
        Account ada = register("Ada");
        String project = project(ada, "Neon Nights");

        JsonNode problem = json(ada.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("sceneNumber", 0, "title", "", "sourceText", " ",
                        "shootDateStart", "2026-10-05", "shootDateEnd", "2026-10-01"))
                .exchange().expectStatus().isBadRequest());

        assertThat(errorFields(problem)).containsExactlyInAnyOrder("sceneNumber", "title", "sourceText", "shootDateEnd");
        assertThat(problem.toString()).doesNotContain("shootWindowValid");
    }

    @Test
    void aDuplicateSceneNumberIsA409() {
        Account ada = register("Ada");
        String project = project(ada, "Neon Nights");
        scene(ada, project, 1, "First");

        ada.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("sceneNumber", 1, "title", "Clash", "sourceText", "text"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentType(PROBLEM)
                .expectBody().jsonPath("$.detail").value(d -> assertThat(d.toString()).contains("scene with that number"));
    }

    @Test
    void scenesComeBackInScriptOrder() {
        Account ada = register("Ada");
        String project = project(ada, "Neon Nights");
        String unnumbered = scene(ada, project, null, "unnumbered");
        String three = scene(ada, project, 3, "three");
        String one = scene(ada, project, 1, "one");

        assertThat(ids(json(ada.client().get().uri("/api/projects/" + project + "/scenes").exchange().expectStatus().isOk())))
                .containsExactly(one, three, unnumbered);
    }

    @Test
    void scenesArePagedInScriptOrder() {
        Account ada = register("Ada");
        String project = project(ada, "Neon");
        String three = scene(ada, project, 3, "Three");
        String unnumbered = scene(ada, project, null, "No number");
        String one = scene(ada, project, 1, "One");

        assertThat(ids(json(ada.client().get().uri("/api/projects/" + project + "/scenes?size=2").exchange().expectStatus().isOk())))
                .containsExactly(one, three);
        JsonNode last = json(ada.client().get().uri("/api/projects/" + project + "/scenes?size=2&page=1").exchange().expectStatus().isOk());
        assertThat(ids(last)).containsExactly(unnumbered);
        assertThat(last.path("totalItems").asLong()).isEqualTo(3);
    }

    @Test
    void editingTheScriptForgetsItsRequirementsButRetitlingKeepsThem() {
        Account ada = register("Ada");
        String scene = scene(ada, project(ada, "Neon Nights"), 1, "A rainy rooftop bar.");
        jdbc.update("UPDATE scenes SET parse_status = 'PARSED', setting_type = 'rooftop bar', parsed_at = now() WHERE id = ?::uuid", scene);

        ada.client().put().uri("/api/scenes/" + scene)
                .bodyValue(Map.of("sceneNumber", 1, "title", "Retitled", "sourceText", "A rainy rooftop bar."))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.parseStatus").isEqualTo("PARSED").jsonPath("$.requirements.settingType").isEqualTo("rooftop bar");

        ada.client().put().uri("/api/scenes/" + scene)
                .bodyValue(Map.of("sceneNumber", 1, "title", "Retitled", "sourceText", "A sunlit greenhouse."))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.parseStatus").isEqualTo("PENDING").jsonPath("$.requirements").isEmpty();
    }

    @Test
    void deletingASceneReturns204() {
        Account ada = register("Ada");
        String scene = scene(ada, project(ada, "Neon Nights"), 1, "text");

        ada.client().delete().uri("/api/scenes/" + scene).exchange().expectStatus().isNoContent();
        ada.client().get().uri("/api/scenes/" + scene).exchange().expectStatus().isNotFound();
    }

    @Test
    void anotherUsersScenesAreA404OnEveryRoute() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String graceProject = project(grace, "Grace's");
        String graceScene = scene(grace, graceProject, 1, "Grace's scene");
        Map<String, Object> body = Map.of("sceneNumber", 2, "title", "Intruder", "sourceText", "text");

        ada.client().post().uri("/api/projects/" + graceProject + "/scenes").bodyValue(body).exchange().expectStatus().isNotFound();
        ada.client().get().uri("/api/projects/" + graceProject + "/scenes").exchange().expectStatus().isNotFound();
        ada.client().get().uri("/api/scenes/" + graceScene).exchange().expectStatus().isNotFound();
        ada.client().put().uri("/api/scenes/" + graceScene).bodyValue(body).exchange().expectStatus().isNotFound();
        ada.client().delete().uri("/api/scenes/" + graceScene).exchange().expectStatus().isNotFound();
        grace.client().get().uri("/api/scenes/" + graceScene).exchange().expectStatus().isOk();
    }

    // --- locations ------------------------------------------------------------------------------

    @Test
    void addingALocationByHandReturns201MarkedManualWithoutAScore() {
        Account ada = register("Ada");
        String scene = scene(ada, project(ada, "Neon Nights"), 1, "text");

        ada.client().post().uri("/api/scenes/" + scene + "/locations")
                .bodyValue(Map.of("name", "My cousin's loft", "address", "12 Water St", "latitude", 40.7, "longitude", -73.99,
                        "sourceUrl", "https://loft.example.com/", "notes", "Great light"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().value("Location", loc -> assertThat(loc).startsWith("/api/locations/"))
                .expectBody()
                .jsonPath("$.sceneId").isEqualTo(scene)
                .jsonPath("$.sourceProvider").isEqualTo("manual")
                .jsonPath("$.status").isEqualTo("SUGGESTED")
                .jsonPath("$.fitScore").isEmpty()
                .jsonPath("$.footprintWarnings").isEmpty();
    }

    @Test
    void locationValidationNamesTheFieldsIncludingTheCoordinatePair() {
        Account ada = register("Ada");
        String scene = scene(ada, project(ada, "Neon Nights"), 1, "text");
        String path = "/api/scenes/" + scene + "/locations";

        assertThat(errorFields(json(ada.client().post().uri(path)
                .bodyValue(Map.of("name", "Loft", "latitude", 40.7)).exchange().expectStatus().isBadRequest())))
                .containsExactly("coordinates");
        assertThat(errorFields(json(ada.client().post().uri(path)
                .bodyValue(Map.of("name", "Loft", "sourceUrl", "javascript:alert(1)")).exchange().expectStatus().isBadRequest())))
                .containsExactly("sourceUrl");
        assertThat(errorFields(json(ada.client().post().uri(path)
                .bodyValue(Map.of("name", " ", "latitude", 95, "longitude", 0)).exchange().expectStatus().isBadRequest())))
                .containsExactlyInAnyOrder("name", "latitude");
    }

    @Test
    void savingTheSamePageTwiceForOneSceneIsA409() {
        Account ada = register("Ada");
        String scene = scene(ada, project(ada, "Neon Nights"), 1, "text");
        location(ada, scene, "Loft", "https://loft.example.com/");

        ada.client().post().uri("/api/scenes/" + scene + "/locations")
                .bodyValue(Map.of("name", "Same page", "sourceUrl", "https://loft.example.com/"))
                .exchange().expectStatus().isEqualTo(409).expectHeader().contentType(PROBLEM);
    }

    @Test
    void locationsAreListedBestFitFirst() {
        Account ada = register("Ada");
        String scene = scene(ada, project(ada, "Neon Nights"), 1, "text");
        String manual = location(ada, scene, "Manual", null);
        String low = location(ada, scene, "Low", null);
        String high = location(ada, scene, "High", null);
        jdbc.update("UPDATE locations SET fit_score = 30 WHERE id = ?::uuid", low);
        jdbc.update("UPDATE locations SET fit_score = 95 WHERE id = ?::uuid", high);

        assertThat(ids(json(ada.client().get().uri("/api/scenes/" + scene + "/locations").exchange().expectStatus().isOk())))
                .containsExactly(high, low, manual);
    }

    @Test
    void locationsArePaged() {
        Account ada = register("Ada");
        String scene = scene(ada, project(ada, "Neon"), 1, "INT. BAR");
        String a = location(ada, scene, "A", null);
        String b = location(ada, scene, "B", null);
        String c = location(ada, scene, "C", null);

        JsonNode page0 = json(ada.client().get().uri("/api/scenes/" + scene + "/locations?size=2").exchange().expectStatus().isOk());
        JsonNode page1 = json(ada.client().get().uri("/api/scenes/" + scene + "/locations?size=2&page=1").exchange().expectStatus().isOk());
        assertThat(ids(page0)).containsExactly(a, b);
        assertThat(ids(page1)).containsExactly(c);
        assertThat(page1.path("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    void theUserCanShortlistAndAnnotateALocationButNothingElse() {
        Account ada = register("Ada");
        String scene = scene(ada, project(ada, "Neon Nights"), 1, "text");
        String id = location(ada, scene, "Loft", null);

        ada.client().put().uri("/api/locations/" + id)
                .bodyValue(Map.of("status", "SHORTLISTED", "notes", "Call the events manager", "name", "Hacked", "fitScore", 100))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SHORTLISTED")
                .jsonPath("$.notes").isEqualTo("Call the events manager")
                .jsonPath("$.name").isEqualTo("Loft")
                .jsonPath("$.fitScore").isEmpty();
        ada.client().put().uri("/api/locations/" + id).bodyValue(Map.of("notes", "no status")).exchange().expectStatus().isBadRequest();
        ada.client().put().uri("/api/locations/" + id).bodyValue(Map.of("status", "MAYBE")).exchange()
                .expectStatus().isBadRequest().expectHeader().contentType(PROBLEM);
    }

    @Test
    void deletingALocationReturns204() {
        Account ada = register("Ada");
        String id = location(ada, scene(ada, project(ada, "Neon Nights"), 1, "text"), "Loft", null);

        ada.client().delete().uri("/api/locations/" + id).exchange().expectStatus().isNoContent();
        ada.client().get().uri("/api/locations/" + id).exchange().expectStatus().isNotFound();
    }

    @Test
    void anotherUsersLocationsAreA404OnEveryRoute() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String graceScene = scene(grace, project(grace, "Grace's"), 1, "text");
        String graceLocation = location(grace, graceScene, "Loft", null);

        ada.client().get().uri("/api/scenes/" + graceScene + "/locations").exchange().expectStatus().isNotFound();
        ada.client().post().uri("/api/scenes/" + graceScene + "/locations").bodyValue(Map.of("name", "X")).exchange().expectStatus().isNotFound();
        ada.client().get().uri("/api/locations/" + graceLocation).exchange().expectStatus().isNotFound();
        ada.client().put().uri("/api/locations/" + graceLocation).bodyValue(Map.of("status", "REJECTED")).exchange().expectStatus().isNotFound();
        ada.client().delete().uri("/api/locations/" + graceLocation).exchange().expectStatus().isNotFound();
        grace.client().get().uri("/api/locations/" + graceLocation).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("SUGGESTED");
    }
}
