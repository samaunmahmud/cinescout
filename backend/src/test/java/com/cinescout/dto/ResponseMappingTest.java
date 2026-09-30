package com.cinescout.dto;

import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.OutreachDraft;
import com.cinescout.domain.OutreachTone;
import com.cinescout.domain.ParseStatus;
import com.cinescout.domain.Project;
import com.cinescout.domain.Scene;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.domain.User;
import com.cinescout.domain.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The entities' ids and timestamps are database-owned, so the tests set them by reflection. */
class ResponseMappingTest {

    private static final Instant CREATED = Instant.parse("2026-09-20T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-09-20T11:00:00Z");

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private static <T> T withDbFields(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(entity, "createdAt", CREATED);
        ReflectionTestUtils.setField(entity, "updatedAt", UPDATED);
        return entity;
    }

    private static User user() {
        return withDbFields(new User("ada@example.com", "$2a$10$secret-hash", "Ada"));
    }

    private static Project project() {
        Project project = new Project(user(), "Neon Nights", "A neo-noir short");
        project.setLocationArea("Brooklyn, New York");
        return withDbFields(project);
    }

    private static Scene scene() {
        Scene scene = withDbFields(new Scene(project(), "Rooftop", "A rainy rooftop bar at night."));
        scene.setSceneNumber(3);
        scene.setShootDateStart(LocalDate.of(2026, 10, 1));
        scene.setShootDateEnd(LocalDate.of(2026, 10, 3));
        return scene;
    }

    private static Location location() {
        Location location = withDbFields(new Location(scene(), "Santana Rooftop"));
        location.setLatitude(new BigDecimal("-37.813600"));
        location.setLongitude(new BigDecimal("144.963100"));
        location.setFitScore((short) 87);
        location.setBookingFriction(BookingFriction.COMMERCIAL);
        location.setFootprintWarnings(List.of("Lift access only"));
        return location;
    }

    @Test
    void userResponseCarriesTheAccountButNeverThePasswordHash() throws Exception {
        User user = user();
        UserResponse response = UserResponse.from(user);

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo("ada@example.com");
        assertThat(response.role()).isEqualTo(UserRole.USER);
        assertThat(json.writeValueAsString(response)).doesNotContain("secret-hash").doesNotContainIgnoringCase("password");
    }

    @Test
    void projectResponseMapsAllFields() {
        Project project = project();
        ProjectResponse response = ProjectResponse.from(project, 12, 3, "https://cdn.example/a.jpg");

        assertThat(response).isEqualTo(new ProjectResponse(project.getId(), "Neon Nights", "A neo-noir short",
                "Brooklyn, New York", project.getStatus(), 12, 3, "https://cdn.example/a.jpg", CREATED, UPDATED));
    }

    @Test
    void unparsedSceneHasNullRequirements() {
        Scene scene = scene();
        SceneResponse response = SceneResponse.from(scene);

        assertThat(response.projectId()).isEqualTo(scene.getProject().getId());
        assertThat(response.parseStatus()).isEqualTo(ParseStatus.PENDING);
        assertThat(response.requirements()).isNull();
        assertThat(response.shootDateEnd()).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    void parsedSceneExposesRequirementsButNotTheRawParserJson() throws Exception {
        Scene scene = scene();
        JsonNode raw = json.readTree("{\"settingType\":\"rooftop bar\",\"internalNote\":\"do-not-leak\"}");
        scene.applyRequirements(new SceneRequirements("rooftop bar", "moody neon", "practical neon", "night",
                AcousticSensitivity.HIGH, 25), raw);

        SceneResponse response = SceneResponse.from(scene);

        assertThat(response.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(response.requirements().acousticSensitivity()).isEqualTo(AcousticSensitivity.HIGH);
        assertThat(response.parsedAt()).isNotNull();
        assertThat(json.writeValueAsString(response)).doesNotContain("do-not-leak");
    }

    @Test
    void locationResponseMapsAssessmentFieldsAndCopiesTheWarnings() {
        Location location = location();
        LocationResponse response = LocationResponse.from(location);

        assertThat(response.sceneId()).isEqualTo(location.getScene().getId());
        assertThat(response.fitScore()).isEqualTo((short) 87);
        assertThat(response.bookingFriction()).isEqualTo(BookingFriction.COMMERCIAL);
        assertThat(response.status()).isEqualTo(LocationStatus.SUGGESTED);
        assertThat(response.footprintWarnings()).containsExactly("Lift access only");
        assertThat(response.logistics()).isNull();
    }

    @Test
    void locationResponseToleratesNullWarningsAndIsImmutable() {
        Location location = location();
        location.setFootprintWarnings(null);

        assertThat(LocationResponse.from(location).footprintWarnings()).isEmpty();

        List<String> warnings = new java.util.ArrayList<>(List.of("Lift access only"));
        location.setFootprintWarnings(warnings);
        LocationResponse response = LocationResponse.from(location);
        warnings.add("added later");

        assertThat(response.footprintWarnings()).containsExactly("Lift access only");
    }

    @Test
    void outreachDraftResponseMapsAllFields() {
        Location location = location();
        OutreachDraft draft = withDbFields(new OutreachDraft(location, user(), "Filming enquiry", "Hello",
                OutreachTone.FRIENDLY));
        draft.setRecipientEmail("owner@example.com");
        draft.setGeneratedBy("ibm/granite");

        OutreachDraftResponse response = OutreachDraftResponse.from(draft);

        assertThat(response.locationId()).isEqualTo(location.getId());
        assertThat(response.subject()).isEqualTo("Filming enquiry");
        assertThat(response.tone()).isEqualTo(OutreachTone.FRIENDLY);
        assertThat(response.recipientEmail()).isEqualTo("owner@example.com");
        assertThat(response.generatedBy()).isEqualTo("ibm/granite");
        assertThat(response.sentAt()).isNull();
    }

    @Test
    void requestPayloadsDeserialiseWithoutTheValidationOnlyGetters() throws Exception {
        SceneRequest request = json.readValue("""
                {"sceneNumber":1,"title":"Rooftop","sourceText":"Rain.","shootDateStart":"2026-10-01"}
                """, SceneRequest.class);

        assertThat(request.shootDateStart()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(json.writeValueAsString(request)).doesNotContain("shootWindowValid");
    }
}
