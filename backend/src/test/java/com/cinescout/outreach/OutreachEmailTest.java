package com.cinescout.outreach;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutreachEmailTest {

    private static ValidatorFactory validators;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validators = Validation.buildDefaultValidatorFactory();
        validator = validators.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validators.close();
    }

    private static List<String> invalid(OutreachEmail email) {
        return validator.validate(email).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString).sorted().toList();
    }

    @Test
    void thePartsAreLaidOutAsAnEmailWithABlankLineBetweenEach() {
        OutreachEmail email = new OutreachEmail("Location enquiry", " Hello Maria, ",
                List.of("I'm Sam from Neon Nights.\n", "  Could we film on your roof?"), "Best wishes,\nSam ");

        assertThat(email.body()).isEqualTo("Hello Maria,\n\nI'm Sam from Neon Nights.\n\nCould we film on your roof?\n\nBest wishes,\nSam");
    }

    @Test
    void blankParagraphsAreDroppedButAnEmailNeedsAtLeastOne() {
        OutreachEmail padded = new OutreachEmail("S", "Hi,", Arrays.asList("Real one", null, "  "), "Sam");

        assertThat(padded.paragraphs()).containsExactly("Real one");
        assertThat(invalid(padded)).isEmpty();
        assertThat(invalid(new OutreachEmail("S", "Hi,", Arrays.asList(" ", null), "Sam"))).containsExactly("paragraphs");
        assertThat(invalid(new OutreachEmail(" ", " ", null, null))).containsExactly("greeting", "paragraphs", "signOff", "subject");
    }
}
