package com.cinescout.logistics;

import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.logistics.LogisticsReport.Environment;
import com.cinescout.logistics.LogisticsReport.NearbyService;
import com.cinescout.logistics.LogisticsReport.NoiseLevel;
import com.cinescout.logistics.LogisticsReport.NoiseSource;
import com.cinescout.logistics.LogisticsReport.SectionStatus;
import com.cinescout.logistics.places.Place;
import com.cinescout.logistics.places.PlaceKind;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns the places around a location into a noise risk and a short list of nearby services.
 *
 * <p>Each noise source scores its kind's loudness (1 to 3) plus 2 in the nearest third of the kind's radius,
 * 1 in the middle third, 0 beyond: 4 or more is HIGH, 3 MEDIUM, less LOW. The location's risk is its loudest
 * source, raised one level for a scene whose sound is sensitive and lowered one for a scene whose isn't (LOW
 * when there is no known source). It is a rule of thumb from the map, not a measurement; the advice says what
 * to check on the recce.
 */
final class EnvironmentAssessor {

    static final int MAX_NOISE_SOURCES = 10;
    static final int SERVICES_PER_KIND = 3;

    private static final Map<PlaceKind, String> ADVICE = advice();

    private EnvironmentAssessor() {
    }

    static Environment assess(List<Place> places, AcousticSensitivity sensitivity) {
        List<NoiseSource> noise = noiseSources(places);
        // With no known source there is nothing for a sensitive scene to be more exposed to.
        NoiseLevel risk = noise.stream().map(NoiseSource::level).max(Comparator.naturalOrder())
                .map(loudest -> adjust(loudest, sensitivity)).orElse(NoiseLevel.LOW);
        return new Environment(SectionStatus.OK, null, sensitivity, risk, noise, services(places));
    }

    static Environment unavailable(String message, AcousticSensitivity sensitivity) {
        return new Environment(SectionStatus.UNAVAILABLE, message, sensitivity, null, List.of(), List.of());
    }

    static NoiseLevel level(PlaceKind kind, double distanceMeters) {
        double third = kind.radiusMeters() / 3.0;
        int proximity = distanceMeters <= third ? 2 : distanceMeters <= 2 * third ? 1 : 0;
        int score = kind.loudness() + proximity;
        return score >= 4 ? NoiseLevel.HIGH : score == 3 ? NoiseLevel.MEDIUM : NoiseLevel.LOW;
    }

    private static NoiseLevel adjust(NoiseLevel level, AcousticSensitivity sensitivity) {
        if (sensitivity == null || sensitivity == AcousticSensitivity.MEDIUM) {
            return level;
        }
        int shifted = level.ordinal() + (sensitivity == AcousticSensitivity.HIGH ? 1 : -1);
        return NoiseLevel.values()[Math.max(0, Math.min(NoiseLevel.values().length - 1, shifted))];
    }

    /**
     * Loudest and nearest first. A road or railway comes back from the map as many pieces, so sources of the
     * same kind and name (or of the same kind, unnamed) are counted once, at their nearest.
     */
    private static List<NoiseSource> noiseSources(List<Place> places) {
        Map<String, Place> nearest = new LinkedHashMap<>();
        for (Place place : places) {
            if (place.kind().group() != PlaceKind.Group.NOISE) {
                continue;
            }
            nearest.merge(place.kind() + "|" + (place.name() == null ? "" : place.name()), place,
                    (a, b) -> a.distanceMeters() <= b.distanceMeters() ? a : b);
        }
        return nearest.values().stream()
                .map(place -> new NoiseSource(place.kind(), place.name(), (int) Math.round(place.distanceMeters()),
                        level(place.kind(), place.distanceMeters()), ADVICE.get(place.kind())))
                .sorted(Comparator.comparing(NoiseSource::level).reversed().thenComparingInt(NoiseSource::distanceMeters))
                .limit(MAX_NOISE_SOURCES)
                .toList();
    }

    /** The nearest few of each service kind, in the order the kinds are declared (hospital first). */
    private static List<NearbyService> services(List<Place> places) {
        Map<PlaceKind, List<Place>> byKind = places.stream()
                .filter(place -> place.kind().group() == PlaceKind.Group.SERVICE)
                .collect(Collectors.groupingBy(Place::kind, () -> new EnumMap<>(PlaceKind.class), Collectors.toList()));
        return byKind.values().stream()
                .flatMap(kind -> kind.stream().sorted(Comparator.comparingDouble(Place::distanceMeters)).limit(SERVICES_PER_KIND))
                .map(place -> new NearbyService(place.kind(), place.name(), (int) Math.round(place.distanceMeters()),
                        place.position() == null ? null : place.position().latitude(),
                        place.position() == null ? null : place.position().longitude()))
                .toList();
    }

    private static Map<PlaceKind, String> advice() {
        Map<PlaceKind, String> advice = new EnumMap<>(PlaceKind.class);
        advice.put(PlaceKind.AIRPORT, "Aircraft overhead: check the flight paths and expect to hold takes");
        advice.put(PlaceKind.HELIPORT, "Helicopter traffic: ask when flights are busiest");
        advice.put(PlaceKind.STADIUM, "Crowd noise and traffic on event days: check the fixtures");
        advice.put(PlaceKind.RAILWAY, "Passing trains: get the timetable and plan takes between them");
        advice.put(PlaceKind.EMERGENCY_STATION, "Sirens at any hour");
        advice.put(PlaceKind.CONSTRUCTION, "Site noise on working days: ask the site about quiet periods");
        advice.put(PlaceKind.MAJOR_ROAD, "Constant traffic noise, worst at rush hour");
        advice.put(PlaceKind.SCHOOL, "Busy at drop-off, breaks and pick-up on school days");
        advice.put(PlaceKind.NIGHTLIFE, "Loud in the evening and late at night, especially at weekends");
        advice.put(PlaceKind.PLACE_OF_WORSHIP, "Bells and gatherings around service times");
        for (PlaceKind kind : PlaceKind.values()) {
            if (kind.group() == PlaceKind.Group.NOISE && !advice.containsKey(kind)) {
                throw new IllegalStateException("No noise advice for " + kind);
            }
        }
        return Map.copyOf(advice);
    }
}
