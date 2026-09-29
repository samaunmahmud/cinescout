package com.cinescout.logistics;

import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.logistics.LogisticsReport.Environment;
import com.cinescout.logistics.LogisticsReport.NearbyService;
import com.cinescout.logistics.LogisticsReport.NoiseLevel;
import com.cinescout.logistics.LogisticsReport.NoiseSource;
import com.cinescout.logistics.LogisticsReport.SectionStatus;
import com.cinescout.logistics.places.Place;
import com.cinescout.logistics.places.PlaceKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class EnvironmentAssessorTest {

    private static final GeoPoint SOMEWHERE = new GeoPoint(40.71, -74.0);

    private static Place place(PlaceKind kind, String name, double distance) {
        return new Place(kind, name, kind.group() == PlaceKind.Group.SERVICE ? SOMEWHERE : null, distance);
    }

    @Test
    void levelsRiseWithLoudnessAndProximity() {
        // Railway: loudness 3, radius 500 m, so thirds at 167 and 333 m.
        assertThat(EnvironmentAssessor.level(PlaceKind.RAILWAY, 100)).isEqualTo(NoiseLevel.HIGH);
        assertThat(EnvironmentAssessor.level(PlaceKind.RAILWAY, 300)).isEqualTo(NoiseLevel.HIGH);
        assertThat(EnvironmentAssessor.level(PlaceKind.RAILWAY, 450)).isEqualTo(NoiseLevel.MEDIUM);
        // Place of worship: loudness 1.
        assertThat(EnvironmentAssessor.level(PlaceKind.PLACE_OF_WORSHIP, 50)).isEqualTo(NoiseLevel.MEDIUM);
        assertThat(EnvironmentAssessor.level(PlaceKind.PLACE_OF_WORSHIP, 150)).isEqualTo(NoiseLevel.LOW);
        // A large airport beyond its radius (found by its outline) still counts, at the lowest proximity.
        assertThat(EnvironmentAssessor.level(PlaceKind.AIRPORT, 9_000)).isEqualTo(NoiseLevel.MEDIUM);
    }

    @Test
    void theRiskIsTheLoudestSourceAdjustedForTheScenesSensitivity() {
        List<Place> places = List.of(place(PlaceKind.RAILWAY, "Main Line", 450)); // MEDIUM

        assertThat(EnvironmentAssessor.assess(places, AcousticSensitivity.MEDIUM).noiseRisk()).isEqualTo(NoiseLevel.MEDIUM);
        assertThat(EnvironmentAssessor.assess(places, null).noiseRisk()).isEqualTo(NoiseLevel.MEDIUM);
        assertThat(EnvironmentAssessor.assess(places, AcousticSensitivity.HIGH).noiseRisk()).isEqualTo(NoiseLevel.HIGH);
        assertThat(EnvironmentAssessor.assess(places, AcousticSensitivity.LOW).noiseRisk()).isEqualTo(NoiseLevel.LOW);
    }

    @Test
    void aQuietPlaceIsLowRiskEvenForASensitiveSceneAndLevelsCapAtHigh() {
        assertThat(EnvironmentAssessor.assess(List.of(), AcousticSensitivity.LOW).noiseRisk()).isEqualTo(NoiseLevel.LOW);
        assertThat(EnvironmentAssessor.assess(List.of(), AcousticSensitivity.HIGH).noiseRisk()).isEqualTo(NoiseLevel.LOW);
        assertThat(EnvironmentAssessor.assess(List.of(place(PlaceKind.RAILWAY, null, 50)), AcousticSensitivity.HIGH).noiseRisk())
                .isEqualTo(NoiseLevel.HIGH);
    }

    @Test
    void piecesOfTheSameRoadCountOnceAtTheirNearestAndSourcesAreLoudestFirst() {
        Environment environment = EnvironmentAssessor.assess(List.of(
                place(PlaceKind.PLACE_OF_WORSHIP, "St Paul's", 50),
                place(PlaceKind.MAJOR_ROAD, "Broadway", 250),
                place(PlaceKind.MAJOR_ROAD, "Broadway", 90),
                place(PlaceKind.MAJOR_ROAD, null, 280),
                place(PlaceKind.MAJOR_ROAD, null, 210)), AcousticSensitivity.MEDIUM);

        assertThat(environment.noiseSources()).extracting(NoiseSource::name, NoiseSource::distanceMeters, NoiseSource::level)
                .containsExactly(
                        tuple("Broadway", 90, NoiseLevel.HIGH),
                        tuple("St Paul's", 50, NoiseLevel.MEDIUM),
                        tuple(null, 210, NoiseLevel.MEDIUM));
        assertThat(environment.noiseSources().getFirst().advice()).isEqualTo("Constant traffic noise, worst at rush hour");
    }

    @Test
    void onlyTheNearestFewServicesOfEachKindAreListedHospitalsFirst() {
        Environment environment = EnvironmentAssessor.assess(List.of(
                place(PlaceKind.FOOD, "Far Cafe", 500),
                place(PlaceKind.FOOD, "Cafe A", 100),
                place(PlaceKind.FOOD, "Cafe B", 200),
                place(PlaceKind.FOOD, "Cafe C", 300),
                place(PlaceKind.HOSPITAL, "General", 2_400.4)), null);

        assertThat(environment.nearbyServices()).extracting(NearbyService::name)
                .containsExactly("General", "Cafe A", "Cafe B", "Cafe C");
        NearbyService hospital = environment.nearbyServices().getFirst();
        assertThat(hospital.distanceMeters()).isEqualTo(2_400);
        assertThat(hospital.latitude()).isEqualTo(40.71);
        assertThat(environment.status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void servicesAreNotNoiseAndNoiseIsNotAService() {
        Environment environment = EnvironmentAssessor.assess(List.of(
                place(PlaceKind.PARKING, "Lot", 100), place(PlaceKind.SCHOOL, "PS 1", 100)), null);

        assertThat(environment.nearbyServices()).extracting(NearbyService::kind).containsExactly(PlaceKind.PARKING);
        assertThat(environment.noiseSources()).extracting(NoiseSource::kind).containsExactly(PlaceKind.SCHOOL);
    }

    @Test
    void noMoreThanTenNoiseSources() {
        List<Place> many = IntStream.range(0, 15)
                .mapToObj(i -> place(PlaceKind.NIGHTLIFE, "Bar " + i, 10 + i)).toList();

        assertThat(EnvironmentAssessor.assess(many, null).noiseSources()).hasSize(EnvironmentAssessor.MAX_NOISE_SOURCES);
    }

    @Test
    void theVenueIsNotItsOwnNeighbour() {
        Place itself = new Place(PlaceKind.FOOD, "Café Noir", SOMEWHERE, 0);
        Place otherCafe = new Place(PlaceKind.FOOD, "Café Blanc", SOMEWHERE, 40);
        Place itsBell = place(PlaceKind.PLACE_OF_WORSHIP, "Café Noir", 20); // same entry, second kind

        Environment environment = EnvironmentAssessor.assess(List.of(itself, otherCafe, itsBell), null, "cafe noir");

        assertThat(environment.nearbyServices()).extracting(NearbyService::name).containsExactly("Café Blanc");
        assertThat(environment.noiseSources()).isEmpty();
        assertThat(EnvironmentAssessor.assess(List.of(itself), null, null).nearbyServices()).hasSize(1);
    }

    @Test
    void namesMatchByWordsIgnoringCaseAccentsPunctuationAndALeadingThe() {
        List<String> venue = EnvironmentAssessor.words("The Blue Note Jazz Club");

        assertThat(EnvironmentAssessor.isVenue(place(PlaceKind.NIGHTLIFE, "Blue Note", 30), venue)).isTrue();
        assertThat(EnvironmentAssessor.isVenue(place(PlaceKind.NIGHTLIFE, "BLUE NOTE JAZZ CLUB!", 30), venue)).isTrue();
        assertThat(EnvironmentAssessor.isVenue(place(PlaceKind.NIGHTLIFE, "Blue Note", EnvironmentAssessor.VENUE_RADIUS_METERS + 1), venue))
                .as("too far away to be the venue").isFalse();
        assertThat(EnvironmentAssessor.isVenue(place(PlaceKind.NIGHTLIFE, "Note Blue", 30), venue)).as("words out of order").isFalse();
        assertThat(EnvironmentAssessor.isVenue(place(PlaceKind.NIGHTLIFE, "Jazz", 30), venue)).as("too short to tell").isFalse();
        assertThat(EnvironmentAssessor.isVenue(place(PlaceKind.NIGHTLIFE, "Bluenote", 30), venue)).isFalse();
        assertThat(EnvironmentAssessor.isVenue(place(PlaceKind.NIGHTLIFE, null, 0), venue)).as("unnamed").isFalse();
        assertThat(EnvironmentAssessor.words("  Crème—Brûlée, the Bar ")).containsExactly("creme", "brulee", "the", "bar");
    }
}
