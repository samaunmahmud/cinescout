package com.cinescout.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A production's budget end to end: lines, sums, the confirmed venues still to budget, and who may change it. */
class BudgetApiTest extends ApiTest {

    private JsonNode json(ResponseSpec response) {
        return response.expectBody(JsonNode.class).returnResult().getResponseBody();
    }

    private String project(Account owner) {
        return json(owner.client().post().uri("/api/projects").bodyValue(Map.of("title", "Night Shift")).exchange().expectStatus().isCreated())
                .path("id").asText();
    }

    private String confirmedVenue(Account owner, String projectId, String name, String quote) {
        String scene = json(owner.client().post().uri("/api/projects/" + projectId + "/scenes")
                .bodyValue(Map.of("title", "Diner", "sourceText", "INT. DINER - NIGHT")).exchange().expectStatus().isCreated()).path("id").asText();
        owner.client().put().uri("/api/scenes/" + scene + "/shoot-dates").bodyValue(Map.of("shootDateStart", "2026-11-02", "shootDateEnd", "2026-11-03"))
                .exchange().expectStatus().isOk();
        String venue = json(owner.client().post().uri("/api/scenes/" + scene + "/locations").bodyValue(Map.of("name", name))
                .exchange().expectStatus().isCreated()).path("id").asText();
        owner.client().put().uri("/api/locations/" + venue + "/contact").bodyValue(Map.of("quote", quote)).exchange().expectStatus().isOk();
        owner.client().put().uri("/api/locations/" + venue).bodyValue(Map.of("status", "CONFIRMED")).exchange().expectStatus().isOk();
        return venue;
    }

    private JsonNode addLine(Account who, String projectId, Map<String, Object> line) {
        return json(who.client().post().uri("/api/projects/" + projectId + "/budget/items").bodyValue(line).exchange().expectStatus().isCreated());
    }

    private static Map<String, Object> line(String category, String label, String amount, String status) {
        Map<String, Object> line = new HashMap<>(Map.of("category", category, "label", label, "amount", amount));
        if (status != null) {
            line.put("status", status);
        }
        return line;
    }

    @Test
    void linesAddUpAgainstTheTotalAndConfirmedVenuesWaitToBeBudgeted() {
        Account ada = register("Ada");
        String project = project(ada);
        String diner = confirmedVenue(ada, project, "Starlite Diner", "£800 a day");

        JsonNode empty = json(ada.client().get().uri("/api/projects/" + project + "/budget").exchange().expectStatus().isOk());
        assertThat(empty.path("currency").asText()).isEqualTo("GBP");
        assertThat(empty.path("total").isNull()).isTrue();
        assertThat(empty.path("unbudgetedVenues").get(0).path("name").asText()).isEqualTo("Starlite Diner");
        assertThat(empty.path("unbudgetedVenues").get(0).path("quote").asText()).isEqualTo("£800 a day");
        assertThat(empty.path("unbudgetedVenues").get(0).path("shootDays").asInt()).isEqualTo(2);

        json(ada.client().put().uri("/api/projects/" + project + "/budget").bodyValue(Map.of("total", "5000", "currency", "EUR"))
                .exchange().expectStatus().isOk());
        Map<String, Object> venueLine = line("VENUE", "Starlite Diner, 2 days", "1600", "COMMITTED");
        venueLine.put("locationId", diner);
        addLine(ada, project, venueLine);
        addLine(ada, project, line("PERMIT", "Street filming permit", "250.50", "PAID"));
        JsonNode budget = addLine(ada, project, line("CATERING", "Lunch for 20", "400", null));

        assertThat(budget.path("currency").asText()).isEqualTo("EUR");
        assertThat(budget.path("totals").path("planned").decimalValue()).isEqualByComparingTo("2250.50");
        assertThat(budget.path("totals").path("committed").decimalValue()).isEqualByComparingTo("1850.50");
        assertThat(budget.path("totals").path("paid").decimalValue()).isEqualByComparingTo("250.50");
        assertThat(budget.path("totals").path("remaining").decimalValue()).isEqualByComparingTo("2749.50");
        assertThat(budget.path("unbudgetedVenues")).isEmpty();
        assertThat(budget.path("items").get(0).path("venueName").asText()).isEqualTo("Starlite Diner");
        assertThat(budget.path("items").get(2).path("status").asText()).isEqualTo("ESTIMATE");
        assertThat(budget.path("byCategory").findValuesAsText("category")).containsExactly("VENUE", "PERMIT", "CATERING");

        String lunch = budget.path("items").get(2).path("id").asText();
        JsonNode changed = json(ada.client().put().uri("/api/budget-items/" + lunch).bodyValue(line("CATERING", "Lunch for 25", "500", "PAID"))
                .exchange().expectStatus().isOk());
        assertThat(changed.path("totals").path("paid").decimalValue()).isEqualByComparingTo("750.50");
        JsonNode removed = json(ada.client().delete().uri("/api/budget-items/" + lunch).exchange().expectStatus().isOk());
        assertThat(removed.path("items")).hasSize(2);

        // Deleting the venue keeps its line, unlinked.
        ada.client().delete().uri("/api/locations/" + diner).exchange().expectStatus().isNoContent();
        JsonNode after = json(ada.client().get().uri("/api/projects/" + project + "/budget").exchange().expectStatus().isOk());
        assertThat(after.path("items").get(0).path("locationId").isNull()).isTrue();
        assertThat(after.path("items").get(0).path("label").asText()).isEqualTo("Starlite Diner, 2 days");
    }

    @Test
    void viewersReadOnlyOutsidersSeeNothingAndLinesStayInTheirProject() {
        Account ada = register("Ada");
        Account vic = register("Vic");
        Account mal = register("Mal");
        String project = project(ada);
        String other = project(ada);
        String elsewhere = confirmedVenue(ada, other, "Elsewhere Bar", "£100");
        ada.client().post().uri("/api/projects/" + project + "/members").bodyValue(Map.of("email", vic.email(), "role", "VIEWER"))
                .exchange().expectStatus().is2xxSuccessful();
        String id = addLine(ada, project, line("OTHER", "Insurance", "300", null)).path("items").get(0).path("id").asText();

        vic.client().get().uri("/api/projects/" + project + "/budget").exchange().expectStatus().isOk();
        vic.client().post().uri("/api/projects/" + project + "/budget/items").bodyValue(line("OTHER", "x", "1", null)).exchange().expectStatus().isForbidden();
        vic.client().put().uri("/api/budget-items/" + id).bodyValue(line("OTHER", "x", "1", null)).exchange().expectStatus().isForbidden();
        mal.client().get().uri("/api/projects/" + project + "/budget").exchange().expectStatus().isNotFound();
        mal.client().delete().uri("/api/budget-items/" + id).exchange().expectStatus().isNotFound();

        Map<String, Object> stray = line("VENUE", "Elsewhere", "100", null);
        stray.put("locationId", elsewhere);
        ada.client().post().uri("/api/projects/" + project + "/budget/items").bodyValue(stray).exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("locationId");
        ada.client().post().uri("/api/projects/" + project + "/budget/items").bodyValue(line("OTHER", "Too precise", "1.234", null))
                .exchange().expectStatus().isBadRequest();
        ada.client().put().uri("/api/projects/" + project + "/budget").bodyValue(Map.of("currency", "pounds")).exchange().expectStatus().isBadRequest();
    }
}
