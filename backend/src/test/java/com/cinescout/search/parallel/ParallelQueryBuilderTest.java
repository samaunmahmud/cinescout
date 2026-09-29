package com.cinescout.search.parallel;

import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.search.LocationSearchRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelQueryBuilderTest {

    private static LocationSearchRequest request(SceneRequirements requirements) {
        return LocationSearchRequest.of(requirements, "Brooklyn, New York");
    }

    private static final SceneRequirements FULL = new SceneRequirements(
            "rooftop bar", "neon noir", "practical neon, no daylight", "night", AcousticSensitivity.HIGH, 12);

    @Test
    void theObjectiveStatesTheAreaTheSettingAndEveryKnownRequirement() {
        String objective = ParallelQueryBuilder.objective(request(FULL));

        assertThat(objective).contains("Brooklyn, New York", "rooftop bar", "Visual mood: neon noir.",
                "Lighting needs: practical neon, no daylight.", "Scene time of day: night.",
                "quiet", "about 12 cast and crew");
    }

    @Test
    void theObjectiveAsksForOneBookableVenuePerResultNotDirectoriesOrArticles() {
        assertThat(ParallelQueryBuilder.objective(request(FULL)))
                .contains("Each result should be about one specific venue")
                .contains("how to enquire about filming or hire")
                .contains("Avoid directories and search-result pages that list many venues");
    }

    @Test
    void unknownRequirementsAreLeftOutRatherThanPrintedAsNull() {
        String objective = ParallelQueryBuilder.objective(
                request(new SceneRequirements("warehouse", null, " ", null, null, null)));

        assertThat(objective).contains("warehouse").doesNotContain("null", "Visual mood", "Lighting", "time of day", "cast and crew");
    }

    @Test
    void acousticSensitivityChangesTheNoiseWording() {
        assertThat(ParallelQueryBuilder.objective(request(with(AcousticSensitivity.HIGH)))).contains("must be quiet");
        assertThat(ParallelQueryBuilder.objective(request(with(AcousticSensitivity.MEDIUM)))).contains("Moderate ambient noise");
        assertThat(ParallelQueryBuilder.objective(request(with(AcousticSensitivity.LOW)))).contains("not a concern");
    }

    private static SceneRequirements with(AcousticSensitivity sensitivity) {
        return new SceneRequirements("studio", null, null, null, sensitivity, null);
    }

    @Test
    void anEmptyCrewEstimateIsNotMentioned() {
        assertThat(ParallelQueryBuilder.objective(request(new SceneRequirements("studio", null, null, null, null, 0))))
                .doesNotContain("cast and crew");
    }

    @Test
    void queriesAreTwoWithoutAMoodAndThreeWithOne() {
        List<String> plain = ParallelQueryBuilder.queries(request(new SceneRequirements("warehouse", null, null, null, null, null)));
        List<String> moody = ParallelQueryBuilder.queries(request(FULL));

        assertThat(plain).containsExactly(
                "warehouse Brooklyn, New York film location hire",
                "warehouse Brooklyn, New York venue hire filming");
        assertThat(moody).hasSize(3).doesNotHaveDuplicates().last().isEqualTo("neon noir rooftop bar Brooklyn, New York");
    }

    @Test
    void modelOutputIsWhitespaceNormalisedAndLengthCapped() {
        String rambling = "an extremely long and rambling description of a place ".repeat(20) + "\n\n\tend";
        SceneRequirements noisy = new SceneRequirements(rambling, rambling, rambling, rambling, null, null);

        assertThat(ParallelQueryBuilder.queries(request(noisy)))
                .allSatisfy(q -> assertThat(q).hasSizeLessThanOrEqualTo(ParallelQueryBuilder.MAX_QUERY_CHARS)
                        .doesNotContain("\n").doesNotContain("\t"));
        assertThat(ParallelQueryBuilder.objective(request(noisy)))
                .hasSizeLessThanOrEqualTo(ParallelQueryBuilder.MAX_OBJECTIVE_CHARS).doesNotContain("\n");
    }

    @Test
    void aNamedVenueIsSoughtByItsNameInTheArea() {
        assertThat(ParallelQueryBuilder.venueObjective("  MEILI   Rooftop ", "Brooklyn, New York"))
                .startsWith("Find the official website of the venue \"MEILI Rooftop\" in Brooklyn, New York")
                .contains("skip directories");
        assertThat(ParallelQueryBuilder.venueQueries("MEILI Rooftop", "Brooklyn, New York"))
                .containsExactly("MEILI Rooftop Brooklyn, New York", "MEILI Rooftop official site");
    }
}
