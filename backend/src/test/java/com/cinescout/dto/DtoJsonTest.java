package com.cinescout.dto;

import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.ProjectStatus;
import com.cinescout.domain.SceneRequirements;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses the application's real, Boot-configured ObjectMapper (no database involved). */
@JsonTest
class DtoJsonTest {

    @Autowired
    ObjectMapper json;

    @Test
    void timestampsAreWrittenAsIsoStrings() throws Exception {
        ProjectResponse response = new ProjectResponse(UUID.randomUUID(), "Neon Nights", null, null,
                ProjectStatus.ACTIVE, 0, 0, null, ProjectRole.OWNER, 0, Instant.parse("2026-09-20T10:00:00Z"), Instant.parse("2026-09-20T11:00:00Z"));

        assertThat(json.writeValueAsString(response)).contains("\"createdAt\":\"2026-09-20T10:00:00Z\"");
    }

    @Test
    void sceneRequirementsRoundTripThroughJson() throws Exception {
        SceneRequirements requirements = new SceneRequirements("rooftop bar", "moody neon", "practical neon",
                "night", AcousticSensitivity.MEDIUM, 25);

        String out = json.writeValueAsString(requirements);

        assertThat(out).contains("\"acousticSensitivity\":\"MEDIUM\"");
        assertThat(json.readValue(out, SceneRequirements.class)).isEqualTo(requirements);
    }
}
