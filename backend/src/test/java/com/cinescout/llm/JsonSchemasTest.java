package com.cinescout.llm;

import com.cinescout.ai.LocationAssessment;
import com.cinescout.domain.SceneRequirements;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemasTest {

    private final JsonSchemas schemas = new JsonSchemas();

    private static List<String> names(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    @Test
    void everyPropertyIsRequiredAndUnknownOnesAreForbidden() {
        JsonNode schema = schemas.schemaFor(LocationAssessment.class);

        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(names(schema.path("required")))
                .containsExactlyInAnyOrder("fitScore", "fitReason", "bookingFriction", "frictionNote", "footprintWarnings");
    }

    @Test
    void constraintsOnTheTypeReachTheSchema() {
        JsonNode props = schemas.schemaFor(LocationAssessment.class).path("properties");

        assertThat(props.path("fitScore").path("minimum").asInt(-1)).isZero();
        assertThat(props.path("fitScore").path("maximum").asInt(-1)).isEqualTo(100);
        assertThat(props.path("fitReason").path("minLength").asInt(0)).isPositive();
        assertThat(names(props.path("bookingFriction").path("enum"))).contains("PUBLIC", "COMMERCIAL", "PRIVATE");
    }

    @Test
    void optionalFieldsMayBeNullButMandatoryOnesMayNot() {
        JsonNode props = schemas.schemaFor(LocationAssessment.class).path("properties");

        assertThat(props.path("frictionNote").toString()).contains("null");
        assertThat(props.path("fitReason").toString()).doesNotContain("null");
        assertThat(props.path("fitScore").toString()).doesNotContain("null");
    }

    @Test
    void aTypeWithoutAnnotationsIsFullyNullable() {
        JsonNode schema = schemas.schemaFor(SceneRequirements.class);

        assertThat(names(schema.path("required"))).containsExactlyInAnyOrder(
                "settingType", "visualMood", "lightingNeeds", "timeOfDay", "acousticSensitivity", "estimatedCastAndCrewSize");
        // A nullable enum is expressed as anyOf: [null, enum].
        JsonNode acoustic = schema.path("properties").path("acousticSensitivity");
        assertThat(acoustic.path("anyOf").toString()).contains("\"type\":\"null\"");
        assertThat(names(acoustic.findValue("enum"))).containsExactly("LOW", "MEDIUM", "HIGH");
        assertThat(schema.path("properties").path("settingType").toString()).contains("null");
    }

    @Test
    void thereIsNoSchemaDialectMarker() {
        assertThat(schemas.schemaFor(LocationAssessment.class).has("$schema")).isFalse();
    }

    @Test
    void callersGetACopyTheyMayModify() {
        ((com.fasterxml.jackson.databind.node.ObjectNode) schemas.schemaFor(LocationAssessment.class)).removeAll();

        assertThat(schemas.schemaFor(LocationAssessment.class).has("properties")).isTrue();
    }
}
