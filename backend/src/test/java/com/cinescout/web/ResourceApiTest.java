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
    void scenesCanBeSearchedByTitleScriptOrSettingIgnoringCaseAndTakingWildcardsLiterally() {
        Account ada = register("Ada");
        String project = project(ada, "Neon");
        String diner = json(ada.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("sceneNumber", 2, "title", "INT. DINER - NIGHT", "sourceText", "Two detectives talk.")).exchange()).path("id").asText();
        String pier = json(ada.client().post().uri("/api/projects/" + project + "/scenes")
                .bodyValue(Map.of("sceneNumber", 1, "title", "EXT. PIER - DAWN", "sourceText", "A detective waits. 100% alone.")).exchange()).path("id").asText();
        String loft = scene(ada, project, 3, "An empty room.");
        jdbc.update("UPDATE scenes SET parse_status = 'PARSED', setting_type = 'Artist''s loft' WHERE id = ?::uuid", loft);
        String base = "/api/projects/" + project + "/scenes?q=";

        assertThat(ids(json(ada.client().get().uri(base + "diner").exchange().expectStatus().isOk()))).containsExactly(diner);
        assertThat(ids(json(ada.client().get().uri(base + "DETECTIVE").exchange().expectStatus().isOk()))).containsExactly(pier, diner);
        assertThat(ids(json(ada.client().get().uri(base + "loft").exchange().expectStatus().isOk()))).containsExactly(loft);
        assertThat(ids(json(ada.client().get().uri(base + "{q}", "100%").exchange().expectStatus().isOk()))).containsExactly(pier);
        assertThat(ids(json(ada.client().get().uri(base + "{q}", "%").exchange().expectStatus().isOk()))).containsExactly(pier);
        assertThat(ids(json(ada.client().get().uri(base + "{q}", "_").exchange().expectStatus().isOk()))).isEmpty();
        assertThat(ids(json(ada.client().get().uri(base + "nothing-like-it").exchange().expectStatus().isOk()))).isEmpty();
        assertThat(ids(json(ada.client().get().uri(base + "{q}", "  ").exchange().expectStatus().isOk()))).hasSize(3);
        JsonNode paged = json(ada.client().get().uri(base + "detective&size=1&page=1").exchange().expectStatus().isOk());
        assertThat(ids(paged)).containsExactly(diner);
        assertThat(paged.path("totalItems").asLong()).isEqualTo(2);
        ada.client().get().uri(base + "x".repeat(101)).exchange().expectStatus().isBadRequest();
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

    // --- a project's locations across its scenes -----------------------------------------------

    private void setStatus(Account owner, String locationId, String status) {
        owner.client().put().uri("/api/locations/" + locationId).bodyValue(Map.of("status", status)).exchange().expectStatus().isOk();
    }

    @Test
    void aProjectsLocationsComeSceneBySceneBestFitFirstEachWithItsScene() {
        Account ada = register("Ada");
        String project = project(ada, "Neon Nights");
        String two = scene(ada, project, 2, "two");
        String one = scene(ada, project, 1, "one");
        String bar = location(ada, two, "Bar", null);
        String low = location(ada, one, "Low", null);
        String high = location(ada, one, "High", null);
        jdbc.update("UPDATE locations SET fit_score = 30, latitude = 40.7, longitude = -73.9 WHERE id = ?::uuid", low);
        jdbc.update("UPDATE locations SET fit_score = 95, logistics_json = '{}'::jsonb WHERE id = ?::uuid", high);
        location(ada, scene(ada, project(ada, "Another project"), 1, "elsewhere"), "Elsewhere", null);

        JsonNode page = json(ada.client().get().uri("/api/projects/" + project + "/locations").exchange().expectStatus().isOk());

        assertThat(ids(page)).containsExactly(high, low, bar);
        assertThat(page.path("totalItems").asLong()).isEqualTo(3);
        JsonNode first = page.path("items").get(0);
        assertThat(first.path("sceneId").asText()).isEqualTo(one);
        assertThat(first.path("sceneNumber").asInt()).isEqualTo(1);
        assertThat(first.path("sceneTitle").asText()).isEqualTo("Scene 1");
        assertThat(first.path("fitScore").asInt()).isEqualTo(95);
        assertThat(first.has("logistics")).isFalse();
        assertThat(page.path("items").get(1).path("latitude").asDouble()).isEqualTo(40.7);

        JsonNode paged = json(ada.client().get().uri("/api/projects/" + project + "/locations?size=2&page=1").exchange().expectStatus().isOk());
        assertThat(ids(paged)).containsExactly(bar);
    }

    @Test
    void aProjectsLocationsCanBeNarrowedToOneStatus() {
        Account ada = register("Ada");
        String project = project(ada, "Neon Nights");
        String scene = scene(ada, project, 1, "one");
        String kept = location(ada, scene, "Kept", null);
        location(ada, scene, "Other", null);
        setStatus(ada, kept, "SHORTLISTED");

        JsonNode shortlisted = json(ada.client().get().uri("/api/projects/" + project + "/locations?status=SHORTLISTED").exchange().expectStatus().isOk());
        assertThat(ids(shortlisted)).containsExactly(kept);
        assertThat(shortlisted.path("totalItems").asLong()).isEqualTo(1);
        assertThat(ids(json(ada.client().get().uri("/api/projects/" + project + "/locations?status=CONFIRMED").exchange().expectStatus().isOk()))).isEmpty();
        ada.client().get().uri("/api/projects/" + project + "/locations?status=NOPE").exchange().expectStatus().isBadRequest();
    }

    @Test
    void progressCountsScenesAndLocationsByStatus() {
        Account ada = register("Ada");
        String project = project(ada, "Neon Nights");
        String one = scene(ada, project, 1, "one");
        String two = scene(ada, project, 2, "two");
        scene(ada, project, 3, "three");
        setStatus(ada, location(ada, one, "A", null), "CONFIRMED");
        setStatus(ada, location(ada, one, "B", null), "CONFIRMED");
        setStatus(ada, location(ada, one, "C", null), "REJECTED");
        location(ada, two, "D", null);
        setStatus(ada, location(ada, scene(ada, project(ada, "Another project"), 1, "elsewhere"), "Elsewhere", null), "CONFIRMED");

        JsonNode progress = json(ada.client().get().uri("/api/projects/" + project + "/progress").exchange().expectStatus().isOk());

        assertThat(progress.path("scenes").asLong()).isEqualTo(3);
        assertThat(progress.path("scenesWithLocations").asLong()).isEqualTo(2);
        assertThat(progress.path("scenesConfirmed").asLong()).isEqualTo(1);
        assertThat(progress.path("locations").asLong()).isEqualTo(4);
        assertThat(progress.path("locationsByStatus").toString())
                .isEqualTo("{\"SUGGESTED\":1,\"SHORTLISTED\":0,\"REJECTED\":1,\"CONTACTED\":0,\"CONFIRMED\":2}");

        JsonNode empty = json(ada.client().get().uri("/api/projects/" + project(ada, "Empty") + "/progress").exchange().expectStatus().isOk());
        assertThat(empty.path("scenes").asLong()).isZero();
        assertThat(empty.path("locationsByStatus").path("CONFIRMED").asLong()).isZero();
    }

    @Test
    void anotherUsersProjectLocationsAndProgressAreA404() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String graceProject = project(grace, "Grace's");
        location(grace, scene(grace, graceProject, 1, "scene"), "Venue", null);

        ada.client().get().uri("/api/projects/" + graceProject + "/locations").exchange().expectStatus().isNotFound();
        ada.client().get().uri("/api/projects/" + graceProject + "/progress").exchange().expectStatus().isNotFound();
        grace.client().get().uri("/api/projects/" + graceProject + "/locations").exchange().expectStatus().isOk();
    }

    @Test
    void aProjectsLocationsDownloadAsASpreadsheetSafeToOpen() {
        Account ada = register("Ada");
        String project = json(ada.client().post().uri("/api/projects").bodyValue(Map.of("title", "Café Noir: Part 2"))
                .exchange().expectStatus().isCreated()).path("id").asText();
        String scene = scene(ada, project, 7, "scene");
        String diner = location(ada, scene, "Tom's \"Famous\" Diner, Brooklyn", "https://diner.example/");
        String trap = location(ada, scene, "=HYPERLINK(\"http://evil.example\")", null);
        jdbc.update("""
                UPDATE locations SET fit_score = 82, booking_friction = 'COMMERCIAL', address = '782 Washington Ave', latitude = 40.674470,
                    longitude = -73.963316, fit_reason = 'Neon and booths', friction_note = 'Ask the owner',
                    footprint_warnings = '["No parking", "Subway noise"]'::jsonb WHERE id = ?::uuid""", diner);
        ada.client().put().uri("/api/locations/" + diner).bodyValue(Map.of("status", "SHORTLISTED", "notes", "Call Tom\nafter 5")).exchange().expectStatus().isOk();

        ada.client().put().uri("/api/locations/" + diner + "/contact")
                .bodyValue(Map.of("name", "Tom Miller", "email", "tom@diner.example", "phone", "+1 718 555 0100")).exchange().expectStatus().isOk();

        String csv = ada.client().get().uri("/api/projects/" + project + "/locations/export").exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("text/csv;charset=UTF-8")
                .expectHeader().valueEquals("Content-Disposition", "attachment; filename=\"cafe-noir-part-2-locations.csv\"")
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(csv.split("\r\n")).containsExactly(
                "\uFEFFScene number,Scene,Venue,Status,Fit score,Booking,Address,Latitude,Longitude,Web page,Why it fits,Booking note,Warnings,Notes,"
                        + "Contact,Contact email,Contact phone",
                "7,Scene 7,\"Tom's \"\"Famous\"\" Diner, Brooklyn\",Shortlisted,82,Commercial,782 Washington Ave,40.674470,-73.963316,"
                        + "https://diner.example/,Neon and booths,Ask the owner,No parking; Subway noise,\"Call Tom\nafter 5\",Tom Miller,tom@diner.example,'+1 718 555 0100",
                "7,Scene 7,\"'=HYPERLINK(\"\"http://evil.example\"\")\",Suggested,,,,,,,,,,,,,");

        String shortlisted = ada.client().get().uri("/api/projects/" + project + "/locations/export?status=SHORTLISTED").exchange()
                .expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
        assertThat(shortlisted.split("\r\n")).hasSize(2);
        assertThat(shortlisted).doesNotContain("HYPERLINK");
        assertThat(trap).isNotBlank();

        register("Grace").client().get().uri("/api/projects/" + project + "/locations/export").exchange().expectStatus().isNotFound();
        web.get().uri("/api/projects/" + project + "/locations/export").exchange().expectStatus().isUnauthorized();
    }

    // --- schedule --------------------------------------------------------------------------------

    private String datedScene(Account owner, String projectId, int number, String start, String end) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("sceneNumber", number, "title", "Scene " + number, "sourceText", "text"));
        if (start != null) {
            body.put("shootDateStart", start);
        }
        if (end != null) {
            body.put("shootDateEnd", end);
        }
        return json(owner.client().post().uri("/api/projects/" + projectId + "/scenes").bodyValue(body)
                .exchange().expectStatus().isCreated()).path("id").asText();
    }

    @Test
    void theScheduleGroupsScenesByTheDayTheirShootStartsWithTheirConfirmedVenues() {
        Account ada = register("Ada");
        String project = project(ada, "Neon Nights");
        String later = datedScene(ada, project, 1, "2026-10-14", "2026-10-15");
        String second = datedScene(ada, project, 2, "2026-10-12", null);
        String first = datedScene(ada, project, 3, "2026-10-12", "2026-10-12");
        String onlyEnd = datedScene(ada, project, 4, null, "2026-10-13");
        String undated = datedScene(ada, project, 5, null, null);
        String venue = location(ada, second, "Wythe Hotel", null);
        location(ada, second, "Runner-up", null);
        setStatus(ada, venue, "CONFIRMED");
        jdbc.update("UPDATE locations SET address = '80 Wythe Ave', latitude = 40.722, longitude = -73.958 WHERE id = ?::uuid", venue);
        ada.client().put().uri("/api/locations/" + venue + "/contact").bodyValue(Map.of("name", "Dana Reyes", "phone", "718 555 0100"))
                .exchange().expectStatus().isOk();
        jdbc.update("UPDATE scenes SET parse_status = 'PARSED', setting_type = 'rooftop bar', time_of_day = 'night' WHERE id = ?::uuid", second);

        JsonNode schedule = json(ada.client().get().uri("/api/projects/" + project + "/schedule").exchange().expectStatus().isOk());

        assertThat(schedule.path("days").findValuesAsText("date")).containsExactly("2026-10-12", "2026-10-13", "2026-10-14");
        JsonNode day = schedule.path("days").get(0).path("scenes");
        assertThat(day.get(0).path("id").asText()).isEqualTo(second);
        assertThat(day.get(1).path("id").asText()).isEqualTo(first);
        assertThat(day.get(0).path("settingType").asText()).isEqualTo("rooftop bar");
        assertThat(day.get(0).path("timeOfDay").asText()).isEqualTo("night");
        assertThat(day.get(0).path("candidates").asInt()).isEqualTo(2);
        assertThat(day.get(0).path("venues")).hasSize(1);
        assertThat(day.get(0).path("venues").get(0).path("id").asText()).isEqualTo(venue);
        assertThat(day.get(0).path("venues").get(0).path("name").asText()).isEqualTo("Wythe Hotel");
        assertThat(day.get(0).path("venues").get(0).path("address").asText()).isEqualTo("80 Wythe Ave");
        assertThat(day.get(0).path("venues").get(0).path("contactName").asText()).isEqualTo("Dana Reyes");
        assertThat(day.get(0).path("venues").get(0).path("contactPhone").asText()).isEqualTo("718 555 0100");
        assertThat(day.get(1).path("venues")).isEmpty();
        assertThat(day.get(1).path("candidates").asInt()).isZero();
        assertThat(day.get(1).path("settingType").isNull()).isTrue();
        assertThat(schedule.path("days").get(1).path("scenes").get(0).path("id").asText()).isEqualTo(onlyEnd);
        JsonNode last = schedule.path("days").get(2).path("scenes").get(0);
        assertThat(last.path("id").asText()).isEqualTo(later);
        assertThat(last.path("shootDateEnd").asText()).isEqualTo("2026-10-15");
        assertThat(schedule.path("unscheduled").findValuesAsText("id")).containsExactly(undated);
    }

    @Test
    void anEmptyProjectHasAnEmptyScheduleAndAnotherUsersIsA404() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String project = project(ada, "Neon Nights");

        JsonNode schedule = json(ada.client().get().uri("/api/projects/" + project + "/schedule").exchange().expectStatus().isOk());
        assertThat(schedule.path("days")).isEmpty();
        assertThat(schedule.path("unscheduled")).isEmpty();
        grace.client().get().uri("/api/projects/" + project + "/schedule").exchange().expectStatus().isNotFound();
    }

    // --- script import ---------------------------------------------------------------------------

    private static final String SCRIPT = """
            NIGHT SHIFT

            INT. DINER - NIGHT

            Rain on the windows.

            EXT. ROOFTOP - DAWN

            The city wakes up below.
            """;

    private static List<Integer> numbers(JsonNode scenes) {
        List<Integer> numbers = new ArrayList<>();
        scenes.forEach(n -> numbers.add(n.path("sceneNumber").isNull() ? null : n.path("sceneNumber").asInt()));
        return numbers;
    }

    @Test
    void importingAScriptAddsASceneForEachHeadingNumberedOnFromTheLastScene() {
        Account ada = register("Ada");
        String project = project(ada, "Night Shift");
        scene(ada, project, 4, "An earlier scene");

        JsonNode imported = json(ada.client().post().uri("/api/projects/" + project + "/scenes/import")
                .bodyValue(Map.of("script", SCRIPT)).exchange().expectStatus().isCreated());

        assertThat(imported.path("scriptNumbersKept").asBoolean()).isFalse();
        assertThat(numbers(imported.path("scenes"))).containsExactly(5, 6);
        assertThat(imported.path("scenes").get(0).path("truncated").asBoolean()).isFalse();
        JsonNode listed = json(ada.client().get().uri("/api/projects/" + project + "/scenes").exchange().expectStatus().isOk());
        assertThat(ids(listed)).hasSize(3).endsWith(imported.path("scenes").get(0).path("id").asText(), imported.path("scenes").get(1).path("id").asText());
        JsonNode diner = listed.path("items").get(1);
        assertThat(diner.path("title").asText()).isEqualTo("INT. DINER - NIGHT");
        assertThat(diner.path("sourceText").asText()).isEqualTo("INT. DINER - NIGHT\n\nRain on the windows.");
        assertThat(diner.path("parseStatus").asText()).isEqualTo("PENDING");
    }

    @Test
    void anImportKeepsTheScriptsOwnNumbersUnlessOneIsTaken() {
        Account ada = register("Ada");
        String project = project(ada, "Night Shift");
        String numbered = "12 INT. DINER - NIGHT 12\nTalk.\n\n14 EXT. ROOFTOP - DAWN 14\nLight.";

        JsonNode first = json(ada.client().post().uri("/api/projects/" + project + "/scenes/import")
                .bodyValue(Map.of("script", numbered)).exchange().expectStatus().isCreated());
        assertThat(first.path("scriptNumbersKept").asBoolean()).isTrue();
        assertThat(numbers(first.path("scenes"))).containsExactly(12, 14);

        JsonNode again = json(ada.client().post().uri("/api/projects/" + project + "/scenes/import")
                .bodyValue(Map.of("script", numbered)).exchange().expectStatus().isCreated());
        assertThat(again.path("scriptNumbersKept").asBoolean()).isFalse();
        assertThat(numbers(again.path("scenes"))).containsExactly(15, 16);
    }

    @Test
    void aPreviewShowsTheScenesWithoutSavingThem() {
        Account ada = register("Ada");
        String project = project(ada, "Night Shift");

        JsonNode preview = json(ada.client().post().uri("/api/projects/" + project + "/scenes/import/preview")
                .bodyValue(Map.of("script", SCRIPT)).exchange().expectStatus().isOk());

        assertThat(numbers(preview.path("scenes"))).containsExactly(1, 2);
        assertThat(preview.path("scenes").get(1).path("title").asText()).isEqualTo("EXT. ROOFTOP - DAWN");
        assertThat(preview.path("scenes").get(1).path("id").isNull()).isTrue();
        assertThat(preview.path("scenes").get(1).path("characters").asInt()).isEqualTo("EXT. ROOFTOP - DAWN\n\nThe city wakes up below.".length());
        assertThat(ids(json(ada.client().get().uri("/api/projects/" + project + "/scenes").exchange().expectStatus().isOk()))).isEmpty();
    }

    @Test
    void aScriptWithoutHeadingsPreviewsAsEmptyAndCannotBeImported() {
        Account ada = register("Ada");
        String project = project(ada, "Night Shift");
        Map<String, String> prose = Map.of("script", "Just some notes about the film.");

        JsonNode preview = json(ada.client().post().uri("/api/projects/" + project + "/scenes/import/preview")
                .bodyValue(prose).exchange().expectStatus().isOk());
        assertThat(preview.path("scenes")).isEmpty();

        JsonNode problem = json(ada.client().post().uri("/api/projects/" + project + "/scenes/import")
                .bodyValue(prose).exchange().expectStatus().isBadRequest().expectHeader().contentType(PROBLEM));
        assertThat(errorFields(problem)).containsExactly("script");
        assertThat(problem.path("errors").get(0).path("message").asText()).contains("No scene headings");

        JsonNode blank = json(ada.client().post().uri("/api/projects/" + project + "/scenes/import")
                .bodyValue(Map.of("script", " ")).exchange().expectStatus().isBadRequest());
        assertThat(errorFields(blank)).containsExactly("script");
    }

    @Test
    void aVeryLongSceneIsCutToTheLengthASceneMayHaveAndAWholeScreenplayFits() {
        Account ada = register("Ada");
        String project = project(ada, "Night Shift");
        String script = "INT. HALL - DAY\n" + "He walks on and on. ".repeat(1_100) + "\n\nEXT. YARD - DAY\n" + "Wind. ".repeat(60_000);
        assertThat(script.length()).isGreaterThan(300_000);

        JsonNode imported = json(ada.client().post().uri("/api/projects/" + project + "/scenes/import")
                .bodyValue(Map.of("script", script)).exchange().expectStatus().isCreated());

        assertThat(imported.path("scenes").get(0).path("truncated").asBoolean()).isTrue();
        assertThat(imported.path("scenes").get(0).path("characters").asInt()).isLessThanOrEqualTo(20_000).isGreaterThan(19_900);
        ada.client().get().uri("/api/scenes/" + imported.path("scenes").get(0).path("id").asText()).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.sourceText").value(text -> assertThat(text.toString()).hasSizeLessThanOrEqualTo(20_000));

        JsonNode tooLong = json(ada.client().post().uri("/api/projects/" + project + "/scenes/import/preview")
                .bodyValue(Map.of("script", "x".repeat(500_001))).exchange().expectStatus().isBadRequest());
        assertThat(errorFields(tooLong)).containsExactly("script");
    }

    @Test
    void aScriptCannotBeImportedIntoAnotherUsersProject() {
        Account ada = register("Ada");
        Account grace = register("Grace");
        String graceProject = project(grace, "Grace's");

        ada.client().post().uri("/api/projects/" + graceProject + "/scenes/import").bodyValue(Map.of("script", SCRIPT)).exchange().expectStatus().isNotFound();
        ada.client().post().uri("/api/projects/" + graceProject + "/scenes/import/preview").bodyValue(Map.of("script", SCRIPT)).exchange().expectStatus().isNotFound();
        assertThat(ids(json(grace.client().get().uri("/api/projects/" + graceProject + "/scenes").exchange().expectStatus().isOk()))).isEmpty();
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
    void aLocationsContactIsSetAsAWholeAndSurvivesAStatusChange() {
        Account ada = register("Ada");
        String location = location(ada, scene(ada, project(ada, "Neon Nights"), 1, "text"), "Tom's Diner", null);

        ada.client().put().uri("/api/locations/" + location + "/contact")
                .bodyValue(Map.of("name", " Tom Miller ", "email", "tom@diner.example", "phone", "+1 (718) 555-0100 x2"))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.contactName").isEqualTo("Tom Miller")
                .jsonPath("$.contactEmail").isEqualTo("tom@diner.example")
                .jsonPath("$.contactPhone").isEqualTo("+1 (718) 555-0100 x2");
        setStatus(ada, location, "CONTACTED");
        ada.client().get().uri("/api/locations/" + location).exchange()
                .expectBody().jsonPath("$.contactName").isEqualTo("Tom Miller").jsonPath("$.status").isEqualTo("CONTACTED");

        // A full replacement: what is left out is cleared.
        ada.client().put().uri("/api/locations/" + location + "/contact").bodyValue(Map.of("phone", "020 7946 0000"))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.contactName").isEmpty().jsonPath("$.contactEmail").isEmpty().jsonPath("$.contactPhone").isEqualTo("020 7946 0000");

        JsonNode problem = json(ada.client().put().uri("/api/locations/" + location + "/contact")
                .bodyValue(Map.of("email", "not-an-email", "phone", "call me maybe")).exchange().expectStatus().isBadRequest());
        assertThat(errorFields(problem)).containsExactlyInAnyOrder("email", "phone");
        register("Grace").client().put().uri("/api/locations/" + location + "/contact").bodyValue(Map.of("name", "Intruder"))
                .exchange().expectStatus().isNotFound();
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
