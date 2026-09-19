package com.cinescout.ai;

import com.cinescout.domain.BookingFriction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Model output is untrusted: these pin down what gets through and what gets rejected. */
class AiRecordsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> invalidProperties(Object dto) {
        return validator.validate(dto).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    // --- SearchResult -------------------------------------------------------------

    @Test
    void searchResultAcceptsAnHttpsHit() {
        assertThat(invalidProperties(new SearchResult("Santana Rooftop", "https://example.com/v", "Rooftop bar...", "parallel")))
                .isEmpty();
    }

    @Test
    void searchResultRejectsBlankFieldsAndNonHttpUrls() {
        assertThat(invalidProperties(new SearchResult(" ", "javascript:alert(1)", null, "")))
                .containsExactlyInAnyOrder("title", "url", "provider");
        assertThat(invalidProperties(new SearchResult("x", "file:///etc/passwd", null, "parallel"))).containsExactly("url");
    }

    // --- LocationAssessment -------------------------------------------------------

    @Test
    void assessmentParsesTheJsonAModelWouldReturn() throws Exception {
        LocationAssessment assessment = json.readValue("""
                {"fitScore": 87, "fitReason": "Neon-lit rooftop with skyline views.",
                 "bookingFriction": "COMMERCIAL", "frictionNote": "Hire enquiry via events team.",
                 "footprintWarnings": ["Lift access only", "Noise curfew 22:00"]}
                """, LocationAssessment.class);

        assertThat(assessment.fitScore()).isEqualTo(87);
        assertThat(assessment.bookingFriction()).isEqualTo(BookingFriction.COMMERCIAL);
        assertThat(assessment.footprintWarnings()).containsExactly("Lift access only", "Noise curfew 22:00");
        assertThat(invalidProperties(assessment)).isEmpty();
    }

    @Test
    void anOmittedWarningsArrayMeansNoWarnings() throws Exception {
        LocationAssessment assessment = json.readValue("""
                {"fitScore": 40, "fitReason": "Too small.", "bookingFriction": "PRIVATE"}
                """, LocationAssessment.class);

        assertThat(assessment.footprintWarnings()).isNotNull().isEmpty();
        assertThat(assessment.frictionNote()).isNull();
        assertThat(invalidProperties(assessment)).isEmpty();
    }

    @Test
    void nullWarningEntriesAreDroppedRatherThanFailingTheWholeAssessment() {
        LocationAssessment assessment = new LocationAssessment(50, "ok", BookingFriction.PUBLIC, null,
                Arrays.asList("Parking is tight", null));

        assertThat(assessment.footprintWarnings()).containsExactly("Parking is tight");
    }

    @Test
    void warningsAreDefensivelyCopied() {
        List<String> source = new ArrayList<>(List.of("Lift access only"));
        LocationAssessment assessment = new LocationAssessment(50, "ok", BookingFriction.PUBLIC, null, source);
        source.add("added later");

        assertThat(assessment.footprintWarnings()).containsExactly("Lift access only");
        assertThatThrownBy(() -> assessment.footprintWarnings().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void outOfRangeScoresAreRejected() {
        assertThat(invalidProperties(new LocationAssessment(101, "ok", BookingFriction.PUBLIC, null, null)))
                .containsExactly("fitScore");
        assertThat(invalidProperties(new LocationAssessment(-1, "ok", BookingFriction.PUBLIC, null, null)))
                .containsExactly("fitScore");
        assertThat(invalidProperties(new LocationAssessment(0, "ok", BookingFriction.PUBLIC, null, null))).isEmpty();
        assertThat(invalidProperties(new LocationAssessment(100, "ok", BookingFriction.PUBLIC, null, null))).isEmpty();
    }

    @Test
    void missingScoreReasonOrFrictionIsRejected() {
        assertThat(invalidProperties(new LocationAssessment(null, " ", null, null, null)))
                .containsExactlyInAnyOrder("fitScore", "fitReason", "bookingFriction");
    }

    @Test
    void blankWarningTextIsRejected() {
        assertThat(invalidProperties(new LocationAssessment(50, "ok", BookingFriction.PUBLIC, null, List.of("  "))))
                .hasSize(1);
    }

    @Test
    void anUnknownBookingFrictionValueFailsDeserialisation() {
        assertThatThrownBy(() -> json.readValue("""
                {"fitScore": 50, "fitReason": "ok", "bookingFriction": "MAYBE"}
                """, LocationAssessment.class)).isInstanceOf(InvalidFormatException.class);
    }
}
