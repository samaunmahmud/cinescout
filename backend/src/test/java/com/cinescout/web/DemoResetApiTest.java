package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared demo account's nightly reset, run through the job endpoint as the scheduled workflow does: a snapshot of
 * its productions and library, then whatever visitors change put back, while every other account is left alone.
 */
@SpringBootTest(properties = "cinescout.jobs.secret=" + DemoResetApiTest.SECRET)
class DemoResetApiTest extends ApiTest {

    static final String SECRET = "test-job-secret";
    private static final String DEMO = "shared-demo@example.com"; // listed in the test config in other capitals

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private JsonNode runJob(String name) {
        return json(web.post().uri("/api/internal/jobs/" + name).header("X-Job-Secret", SECRET).exchange().expectStatus().isOk());
    }

    private WebTestClient demo() {
        web.post().uri("/api/auth/register").bodyValue(Map.of("email", DEMO, "password", PASSWORD, "displayName", "Demo"))
                .exchange().expectStatus().isCreated();
        return as(DEMO, PASSWORD);
    }

    private String create(WebTestClient who, String uri, Map<String, ?> body) {
        return json(who.post().uri(uri).bodyValue(body).exchange().expectStatus().is2xxSuccessful()).path("id").asText();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void visitorsChangesAreUndoneAndTheDemoComesBackRowForRow() {
        WebTestClient demo = demo();
        Account crew = register("Cam");
        String project = create(demo, "/api/projects", Map.of("title", "The Night Ferry"));
        demo.post().uri("/api/projects/" + project + "/members").bodyValue(Map.of("email", crew.email(), "role", "EDITOR"))
                .exchange().expectStatus().is2xxSuccessful();
        String scene = create(demo, "/api/projects/" + project + "/scenes",
                Map.of("sceneNumber", 1, "title", "Ferry deck", "sourceText", "EXT. FERRY DECK - NIGHT"));
        String venue = create(demo, "/api/scenes/" + scene + "/locations", Map.of("name", "Harbour Pier", "notes", "Ask for Lou"));
        String comment = create(crew.client(), "/api/locations/" + venue + "/comments", Map.of("body", "Love the lamps"));
        create(demo, "/api/projects/" + project + "/budget/items",
                Map.of("category", "VENUE", "label", "Pier fee", "amount", 1200, "locationId", venue));
        create(demo, "/api/scenes/" + scene + "/shots", Map.of("description", "Wide on the rail", "locationId", venue));
        String saved = create(demo, "/api/library", Map.of("locationId", venue));
        jdbc.update("""
                INSERT INTO outreach_drafts (location_id, created_by, subject, body, status, reply_token)
                SELECT ?::uuid, id, 'Filming at Harbour Pier', 'Hello', 'DRAFT', md5(random()::text) FROM users WHERE email = ?""",
                venue, crew.email());
        Object created = jdbc.queryForObject("SELECT created_at FROM locations WHERE id = ?::uuid", Object.class, venue);
        int activity = count("SELECT count(*) FROM activity WHERE project_id = ?::uuid", project);
        assertThat(activity).isPositive();

        // A bystander's own production is never touched.
        Account ada = register("Ada");
        String own = create(ada.client(), "/api/projects", Map.of("title", "Not a demo"));

        assertThat(runJob("demo-snapshot").path("affected").asInt()).isGreaterThan(8);

        // Visitors rename, delete and add.
        demo.put().uri("/api/projects/" + project).bodyValue(Map.of("title", "Vandalised", "status", "ACTIVE")).exchange().expectStatus().isOk();
        demo.delete().uri("/api/scenes/" + scene).exchange().expectStatus().isNoContent();
        demo.delete().uri("/api/library/" + saved).exchange().expectStatus().isNoContent();
        create(demo, "/api/projects", Map.of("title", "A visitor's film"));
        ada.client().put().uri("/api/projects/" + own).bodyValue(Map.of("title", "Still mine", "status", "ACTIVE")).exchange().expectStatus().isOk();

        JsonNode run = runJob("demo-reset");
        assertThat(run.path("job").asText()).isEqualTo("demo-reset");
        assertThat(run.path("affected").asInt()).isGreaterThan(8);

        JsonNode projects = json(demo.get().uri("/api/projects").exchange().expectStatus().isOk());
        assertThat(projects.path("items").findValuesAsText("title")).containsExactly("The Night Ferry");
        assertThat(json(demo.get().uri("/api/locations/" + venue).exchange().expectStatus().isOk()).path("notes").asText())
                .isEqualTo("Ask for Lou");
        assertThat(jdbc.queryForObject("SELECT created_at FROM locations WHERE id = ?::uuid", Object.class, venue)).isEqualTo(created);
        assertThat(json(crew.client().get().uri("/api/locations/" + venue + "/comments").exchange().expectStatus().isOk())
                .path("items").findValuesAsText("id")).containsExactly(comment);
        assertThat(json(demo.get().uri("/api/library").exchange().expectStatus().isOk()).path("items").findValuesAsText("id"))
                .containsExactly(saved);
        assertThat(count("SELECT count(*) FROM budget_items WHERE location_id = ?::uuid", venue)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM shots WHERE scene_id = ?::uuid", scene)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outreach_drafts WHERE location_id = ?::uuid", venue)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM project_members WHERE project_id = ?::uuid", project)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM activity WHERE project_id = ?::uuid", project)).isEqualTo(activity);
        assertThat(json(ada.client().get().uri("/api/projects/" + own).exchange().expectStatus().isOk()).path("title").asText())
                .isEqualTo("Still mine");

        // A second run changes nothing visible.
        runJob("demo-reset");
        assertThat(json(demo.get().uri("/api/projects").exchange().expectStatus().isOk()).path("items").findValuesAsText("title"))
                .containsExactly("The Night Ferry");
    }

    @Test
    void aCrewMemberWhoLeftSinceTheSnapshotIsLeftOutAndTheirWordsKeepNoAuthor() {
        WebTestClient demo = demo();
        Account crew = register("Cam");
        String project = create(demo, "/api/projects", Map.of("title", "The Night Ferry"));
        demo.post().uri("/api/projects/" + project + "/members").bodyValue(Map.of("email", crew.email(), "role", "EDITOR"))
                .exchange().expectStatus().is2xxSuccessful();
        String scene = create(demo, "/api/projects/" + project + "/scenes",
                Map.of("sceneNumber", 1, "title", "Ferry deck", "sourceText", "EXT. FERRY DECK - NIGHT"));
        String venue = create(demo, "/api/scenes/" + scene + "/locations", Map.of("name", "Harbour Pier"));
        String comment = create(crew.client(), "/api/locations/" + venue + "/comments", Map.of("body", "Love the lamps"));
        runJob("demo-snapshot");

        jdbc.update("DELETE FROM users WHERE id = ?", crew.id());
        runJob("demo-reset");

        assertThat(count("SELECT count(*) FROM project_members WHERE project_id = ?::uuid", project)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT author_id FROM venue_comments WHERE id = ?::uuid", UUID.class, comment)).isNull();
    }

    @Test
    void theFirstResetTakesTheSnapshotAndOrdinaryAccountsHaveNone() {
        WebTestClient demo = demo();
        create(demo, "/api/projects", Map.of("title", "The Night Ferry"));
        create(register("Ada").client(), "/api/projects", Map.of("title", "Not a demo"));

        assertThat(runJob("demo-reset").path("affected").asInt()).isZero();
        assertThat(count("SELECT count(*) FROM demo_snapshots")).isEqualTo(1);

        String extra = create(demo, "/api/projects", Map.of("title", "A visitor's film"));
        runJob("demo-reset");
        demo.get().uri("/api/projects/" + extra).exchange().expectStatus().isNotFound();
        assertThat(json(demo.get().uri("/api/projects").exchange().expectStatus().isOk()).path("items").findValuesAsText("title"))
                .containsExactly("The Night Ferry");
    }
}
