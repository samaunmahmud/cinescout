package com.cinescout.dto;

import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.OutreachStatus;
import com.cinescout.domain.OutreachTone;
import com.cinescout.domain.ProjectStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** The names of the properties that failed validation. */
    private static Set<String> invalidProperties(Object dto) {
        return validator.validate(dto).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static String repeat(int n) {
        return "x".repeat(n);
    }

    // --- register -----------------------------------------------------------------

    @Test
    void registerAcceptsAValidPayload() {
        assertThat(invalidProperties(new RegisterRequest("ada@example.com", "correct horse", "Ada"))).isEmpty();
    }

    @Test
    void registerRejectsBadEmailShortPasswordAndBlankName() {
        assertThat(invalidProperties(new RegisterRequest("not-an-email", "short", " ")))
                .containsExactlyInAnyOrder("email", "password", "displayName");
    }

    @Test
    void registerRejectsPasswordsBcryptWouldTruncate() {
        assertThat(invalidProperties(new RegisterRequest("ada@example.com", repeat(73), "Ada")))
                .containsExactly("password");
    }

    @Test
    void registerToStringNeverContainsThePassword() {
        String text = new RegisterRequest("ada@example.com", "s3cret-passphrase", "Ada").toString();

        assertThat(text).doesNotContain("s3cret-passphrase").contains("ada@example.com");
    }

    // --- project ------------------------------------------------------------------

    @Test
    void createProjectRequiresANonBlankTitle() {
        assertThat(invalidProperties(new CreateProjectRequest("Neon Nights", null, null))).isEmpty();
        assertThat(invalidProperties(new CreateProjectRequest("  ", null, null))).containsExactly("title");
        assertThat(invalidProperties(new CreateProjectRequest(repeat(201), null, null))).containsExactly("title");
    }

    @Test
    void projectLocationAreaIsOptionalButBounded() {
        assertThat(invalidProperties(new CreateProjectRequest("Neon Nights", null, "Brooklyn, New York"))).isEmpty();
        assertThat(invalidProperties(new CreateProjectRequest("Neon Nights", null, repeat(201)))).containsExactly("locationArea");
        assertThat(invalidProperties(new UpdateProjectRequest("Neon Nights", null, repeat(201), ProjectStatus.ACTIVE)))
                .containsExactly("locationArea");
    }

    @Test
    void updateProjectRequiresTitleAndStatus() {
        assertThat(invalidProperties(new UpdateProjectRequest("Neon Nights", null, null, ProjectStatus.ARCHIVED))).isEmpty();
        assertThat(invalidProperties(new UpdateProjectRequest("", null, null, null)))
                .containsExactlyInAnyOrder("title", "status");
    }

    // --- scene --------------------------------------------------------------------

    @Test
    void sceneAcceptsAMissingOrOrderedShootWindow() {
        LocalDate start = LocalDate.of(2026, 10, 1);

        assertThat(invalidProperties(new SceneRequest(1, "Rooftop", "A rainy bar.", null, null))).isEmpty();
        assertThat(invalidProperties(new SceneRequest(1, "Rooftop", "A rainy bar.", start, null))).isEmpty();
        assertThat(invalidProperties(new SceneRequest(1, "Rooftop", "A rainy bar.", start, start))).isEmpty();
        assertThat(invalidProperties(new SceneRequest(1, "Rooftop", "A rainy bar.", start, start.plusDays(2)))).isEmpty();
    }

    @Test
    void sceneRejectsAShootWindowThatEndsBeforeItStarts() {
        LocalDate start = LocalDate.of(2026, 10, 3);

        assertThat(invalidProperties(new SceneRequest(1, "Rooftop", "A rainy bar.", start, start.minusDays(1))))
                .containsExactly("shootWindowValid");
    }

    @Test
    void sceneRejectsBlankTextNonPositiveNumberAndOversizedText() {
        assertThat(invalidProperties(new SceneRequest(0, "", " ", null, null)))
                .containsExactlyInAnyOrder("sceneNumber", "title", "sourceText");
        assertThat(invalidProperties(new SceneRequest(1, "Rooftop", repeat(20_001), null, null)))
                .containsExactly("sourceText");
    }

    // --- location -----------------------------------------------------------------

    private static CreateLocationRequest location(String url, BigDecimal lat, BigDecimal lng) {
        return new CreateLocationRequest("Santana Rooftop", null, lat, lng, url, null);
    }

    @Test
    void locationAcceptsHttpsUrlAndValidCoordinates() {
        assertThat(invalidProperties(location("https://example.com/venue", new BigDecimal("-37.8136"), new BigDecimal("144.9631"))))
                .isEmpty();
        assertThat(invalidProperties(location(null, null, null))).isEmpty();
    }

    @Test
    void locationRejectsNonHttpUrls() {
        assertThat(invalidProperties(location("javascript:alert(1)", null, null))).containsExactly("sourceUrl");
        assertThat(invalidProperties(location("data:text/html,hi", null, null))).containsExactly("sourceUrl");
        assertThat(invalidProperties(location("ftp://example.com/x", null, null))).containsExactly("sourceUrl");
    }

    @Test
    void locationRejectsOutOfRangeAndUnpairedCoordinates() {
        assertThat(invalidProperties(location(null, new BigDecimal("91"), new BigDecimal("0")))).containsExactly("latitude");
        assertThat(invalidProperties(location(null, new BigDecimal("0"), new BigDecimal("-181")))).containsExactly("longitude");
        assertThat(invalidProperties(location(null, new BigDecimal("10"), null))).containsExactly("coordinatePairComplete");
    }

    @Test
    void updateLocationRequiresAStatus() {
        assertThat(invalidProperties(new UpdateLocationRequest(LocationStatus.SHORTLISTED, "Call Monday"))).isEmpty();
        assertThat(invalidProperties(new UpdateLocationRequest(null, null))).containsExactly("status");
    }

    // --- outreach -----------------------------------------------------------------

    @Test
    void generateOutreachDefaultsTheToneToProfessional() {
        assertThat(new GenerateOutreachRequest(null, null, null, null).tone()).isEqualTo(OutreachTone.PROFESSIONAL);
        assertThat(new GenerateOutreachRequest(OutreachTone.CONCISE, null, null, null).tone()).isEqualTo(OutreachTone.CONCISE);
    }

    @Test
    void generateOutreachRejectsAMalformedRecipientEmail() {
        assertThat(invalidProperties(new GenerateOutreachRequest(null, "Sam", "nope", null)))
                .containsExactly("recipientEmail");
    }

    @Test
    void updateOutreachRequiresSubjectBodyToneAndStatus() {
        assertThat(invalidProperties(new UpdateOutreachRequest("Filming enquiry", "Hello", OutreachTone.FRIENDLY,
                OutreachStatus.DRAFT, null, null))).isEmpty();
        assertThat(invalidProperties(new UpdateOutreachRequest(" ", "", null, null, null, null)))
                .containsExactlyInAnyOrder("subject", "body", "tone", "status");
    }

    @Test
    void violationsCarryHumanReadableMessages() {
        Set<ConstraintViolation<SceneRequest>> violations = validator.validate(
                new SceneRequest(1, "Rooftop", "A rainy bar.", LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 1)));

        assertThat(violations).singleElement()
                .extracting(ConstraintViolation::getMessage)
                .isEqualTo("shootDateEnd must not be before shootDateStart");
    }
}
