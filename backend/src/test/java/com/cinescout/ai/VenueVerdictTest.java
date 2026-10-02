package com.cinescout.ai;

import com.cinescout.ai.VenueVerdict.Evidence;
import com.cinescout.ai.VenueVerdict.SettingMatch;
import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.SceneRequirements;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cinescout.ai.VenueVerdict.Evidence.FAILS;
import static com.cinescout.ai.VenueVerdict.Evidence.MEETS;
import static com.cinescout.ai.VenueVerdict.Evidence.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

class VenueVerdictTest {

    private static final SceneRequirements EVERYTHING =
            new SceneRequirements("diner", "neon noir", "practical neon", "night", AcousticSensitivity.HIGH, 20);
    private static final SceneRequirements SETTING_ONLY = new SceneRequirements("diner", null, null, null, null, null);

    private static VenueVerdict verdict(SettingMatch setting, Evidence mood, Evidence lighting, Evidence timeOfDay,
                                        Evidence sound, Evidence capacity) {
        return new VenueVerdict(true, "Moonlight Diner", null, false, setting, mood, lighting, timeOfDay, sound, capacity,
                "Chrome counter and neon sign.", BookingFriction.COMMERCIAL, null, null, null);
    }

    private static VenueVerdict all(SettingMatch setting, Evidence evidence) {
        return verdict(setting, evidence, evidence, evidence, evidence, evidence);
    }

    @Test
    void theKindOfPlaceSetsTheStartingScore() {
        assertThat(all(SettingMatch.EXACT, UNKNOWN).fitScore(EVERYTHING)).isEqualTo(70);
        assertThat(all(SettingMatch.CLOSE, UNKNOWN).fitScore(EVERYTHING)).isEqualTo(55);
        assertThat(all(SettingMatch.DRESSABLE, UNKNOWN).fitScore(EVERYTHING)).isEqualTo(35);
        assertThat(all(SettingMatch.UNSUITABLE, UNKNOWN).fitScore(EVERYTHING)).isEqualTo(15);
    }

    @Test
    void eachRequirementTheVenueClearlyMeetsOrFailsMovesTheScore() {
        assertThat(all(SettingMatch.EXACT, MEETS).fitScore(EVERYTHING)).isEqualTo(100);
        assertThat(verdict(SettingMatch.EXACT, MEETS, MEETS, UNKNOWN, UNKNOWN, UNKNOWN).fitScore(EVERYTHING)).isEqualTo(82);
        assertThat(verdict(SettingMatch.EXACT, FAILS, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN).fitScore(EVERYTHING)).isEqualTo(62);
    }

    @Test
    void tooSmallOrTooLoudCostsMoreThanTheWrongMood() {
        int wrongMood = verdict(SettingMatch.EXACT, FAILS, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN).fitScore(EVERYTHING);
        int tooLoud = verdict(SettingMatch.EXACT, UNKNOWN, UNKNOWN, UNKNOWN, FAILS, UNKNOWN).fitScore(EVERYTHING);
        int tooSmall = verdict(SettingMatch.EXACT, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, FAILS).fitScore(EVERYTHING);

        assertThat(tooLoud).isEqualTo(58).isLessThan(wrongMood);
        assertThat(tooSmall).isEqualTo(58);
    }

    @Test
    void requirementsTheSceneDoesNotStateAreNotCounted() {
        assertThat(all(SettingMatch.EXACT, MEETS).fitScore(SETTING_ONLY)).isEqualTo(70);
        assertThat(all(SettingMatch.EXACT, FAILS).fitScore(SETTING_ONLY)).isEqualTo(70);
    }

    @Test
    void soundCountsOnlyForASceneThatIsSensitiveToIt() {
        SceneRequirements noisyIsFine = new SceneRequirements("diner", null, null, null, AcousticSensitivity.LOW, null);
        SceneRequirements someQuiet = new SceneRequirements("diner", null, null, null, AcousticSensitivity.MEDIUM, null);

        assertThat(verdict(SettingMatch.EXACT, UNKNOWN, UNKNOWN, UNKNOWN, FAILS, UNKNOWN).fitScore(noisyIsFine)).isEqualTo(70);
        assertThat(verdict(SettingMatch.EXACT, UNKNOWN, UNKNOWN, UNKNOWN, FAILS, UNKNOWN).fitScore(someQuiet)).isEqualTo(58);
    }

    @Test
    void aVenueElsewhereOrAPageOfSeveralVenuesScoresZero() {
        VenueVerdict elsewhere = new VenueVerdict(true, "Moonlight Diner", null, true, SettingMatch.EXACT, MEETS, MEETS, MEETS,
                MEETS, MEETS, "In Chicago.", BookingFriction.COMMERCIAL, null, null, null);

        assertThat(elsewhere.fitScore(EVERYTHING)).isZero();
        assertThat(Verdicts.directory("A", "B").fitScore(EVERYTHING)).isZero();
    }

    @Test
    void aUsableVenueNeverScoresZeroSoItIsNeverMistakenForAnUnusableOne() {
        assertThat(all(SettingMatch.UNSUITABLE, FAILS).fitScore(EVERYTHING)).isEqualTo(1);
    }

    @Test
    void theAssessmentCarriesTheScoreAndTheModelsOtherAnswers() {
        VenueVerdict verdict = new VenueVerdict(true, " Moonlight Diner ", "12 Main St", false, SettingMatch.EXACT, MEETS,
                UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, "Neon sign.", BookingFriction.COMMERCIAL, "Hire enquiry.",
                List.of("Narrow door"), List.of());

        LocationAssessment assessment = verdict.toAssessment(EVERYTHING);

        assertThat(assessment.fitScore()).isEqualTo(76);
        assertThat(assessment.singleVenue()).isTrue();
        assertThat(assessment.venueName()).isEqualTo("Moonlight Diner");
        assertThat(assessment.address()).isEqualTo("12 Main St");
        assertThat(assessment.fitReason()).isEqualTo("Neon sign.");
        assertThat(assessment.frictionNote()).isEqualTo("Hire enquiry.");
        assertThat(assessment.footprintWarnings()).containsExactly("Narrow door");
        assertThat(assessment.listedVenues()).isEmpty();
    }

    @Test
    void parsesTheJsonAModelWouldReturn() throws Exception {
        VenueVerdict verdict = new ObjectMapper().readValue("""
                {"singleVenue": true, "venueName": "Moonlight Diner", "address": null, "outsideSearchArea": false,
                 "setting": "CLOSE", "mood": "MEETS", "lighting": "UNKNOWN", "timeOfDay": "FAILS", "sound": "UNKNOWN",
                 "capacity": "UNKNOWN", "fitReason": "A cafe with a neon sign that closes at 6 pm.",
                 "bookingFriction": "COMMERCIAL", "frictionNote": null, "footprintWarnings": [], "listedVenues": []}
                """, VenueVerdict.class);

        assertThat(verdict.fitScore(EVERYTHING)).isEqualTo(55 + 6 - 8);
    }
}
