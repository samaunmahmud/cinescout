package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiTest extends ApiTest {

    /** The application's own routes; Actuator registers a second mapping of this type for its endpoints. */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping routes;

    /**
     * The first request builds the whole description, which takes seconds now that the API has grown, and longer
     * on a busy machine: more than the client's default five.
     */
    private JsonNode docs() {
        return web.mutate().responseTimeout(Duration.ofSeconds(30)).build()
                .get().uri("/v3/api-docs").exchange().expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    @Test
    void theDescriptionIsPublicAndNamesTheApi() {
        JsonNode docs = docs(); // no credentials

        assertThat(docs.path("openapi").asText()).startsWith("3.");
        assertThat(docs.path("info").path("title").asText()).isEqualTo("CineScout API");
        assertThat(docs.path("info").path("description").asText()).contains("HTTP Basic").contains("RFC 9457");
    }

    @Test
    void everyApiRouteIsDocumented() {
        JsonNode paths = docs().path("paths");
        List<String> missing = new ArrayList<>();
        int checked = 0;

        for (var entry : routes.getHandlerMethods().entrySet()) {
            for (var pattern : entry.getKey().getPatternsCondition().getPatterns()) {
                if (!pattern.getPatternString().startsWith("/api/")) {
                    continue;
                }
                for (RequestMethod method : entry.getKey().getMethodsCondition().getMethods()) {
                    checked++;
                    if (paths.path(pattern.getPatternString()).path(method.name().toLowerCase()).isMissingNode()) {
                        missing.add(method + " " + pattern.getPatternString());
                    }
                }
            }
        }

        assertThat(checked).as("routes found (2 auth + 5 projects + 5 scenes + 5 locations + 2 scouting)").isGreaterThanOrEqualTo(19);
        assertThat(missing).as("routes missing from the OpenAPI description").isEmpty();
    }

    @Test
    void everyOperationHasASummary() {
        List<String> unsummarised = new ArrayList<>();
        docs().path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(op -> {
            if (op.getValue().path("summary").asText().isBlank()) {
                unsummarised.add(op.getKey().toUpperCase() + " " + path.getKey());
            }
        }));

        assertThat(unsummarised).isEmpty();
    }

    @Test
    void basicAuthIsRequiredEverywhereExceptRegistration() {
        JsonNode docs = docs();

        assertThat(docs.path("components").path("securitySchemes").path("basicAuth").path("scheme").asText()).isEqualTo("basic");
        assertThat(docs.path("security").toString()).contains("basicAuth");
        JsonNode register = docs.path("paths").path("/api/auth/register").path("post");
        assertThat(register.path("security").isArray()).isTrue();
        assertThat(register.path("security")).isEmpty(); // an empty list overrides the global requirement
        assertThat(docs.path("paths").path("/api/auth/me").path("get").has("security")).isFalse();
    }

    @Test
    void creationsDocument201AndDeletionsDocument204() {
        JsonNode paths = docs().path("paths");

        for (String created : new String[] {"/api/projects", "/api/projects/{projectId}/scenes", "/api/scenes/{sceneId}/locations"}) {
            assertThat(paths.path(created).path("post").path("responses").has("201")).as(created).isTrue();
        }
        assertThat(paths.path("/api/auth/register").path("post").path("responses").has("201")).isTrue();
        for (String deleted : new String[] {"/api/projects/{projectId}", "/api/scenes/{sceneId}", "/api/locations/{locationId}"}) {
            assertThat(paths.path(deleted).path("delete").path("responses").has("204")).as(deleted).isTrue();
        }
    }

    @Test
    void theScoutingEndpointsDocumentTheirFailureModes() {
        JsonNode responses = docs().path("paths").path("/api/scenes/{sceneId}/scout").path("post").path("responses");

        assertThat(responses.has("409")).isTrue();
        assertThat(responses.has("502")).isTrue();
        assertThat(responses.has("503")).isTrue();
        JsonNode maxResults = docs().path("paths").path("/api/scenes/{sceneId}/scout").path("post").path("parameters");
        assertThat(maxResults.toString()).contains("maxResults").contains("\"maximum\":20");
    }

    @Test
    void nothingInternalLeaksIntoTheSchemas() {
        String docs = docs().toString();

        assertThat(docs).doesNotContain("AuthenticatedUser").doesNotContain("passwordHash").doesNotContain("password_hash");
        // The request has a password; no response schema does.
        JsonNode schemas = docs().path("components").path("schemas");
        assertThat(schemas.path("UserResponse").path("properties").has("password")).isFalse();
        assertThat(schemas.path("RegisterRequest").path("properties").has("password")).isTrue();
    }

    @Test
    void theSwaggerUiIsReachableWithoutALogin() {
        web.get().uri("/swagger-ui.html").exchange().expectStatus().value(status -> assertThat(status).isBetween(200, 399));
        web.get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk();
    }

    @Test
    void theRestOfTheApiStillNeedsALogin() {
        web.mutate().build().method(HttpMethod.GET).uri("/api/projects").exchange().expectStatus().isUnauthorized();
    }
}
