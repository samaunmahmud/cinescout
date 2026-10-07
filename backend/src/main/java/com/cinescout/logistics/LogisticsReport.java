package com.cinescout.logistics;

import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.logistics.places.PlaceKind;
import com.cinescout.logistics.solar.SceneLight;
import com.cinescout.logistics.solar.SolarDay;
import com.cinescout.logistics.weather.WeatherPlan.Basis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Module B's answer for one location: the light, the weather and the surroundings on the shoot days.
 * It is cached on the location ({@code logistics_json}) exactly as it is returned. Weather and
 * surroundings come from outside services and can each be missing (see {@link SectionStatus}); the
 * solar section is computed locally and is always there.
 *
 * @param version     the shape of this record, bumped when it changes incompatibly
 * @param timeZone    the IANA zone every local time in the report is in; UTC if it could not be resolved
 * @param notes       caveats about the report as a whole, for the user to read
 * @param attribution credits the data licences require to be shown with the data
 */
public record LogisticsReport(
        int version,
        Instant generatedAt,
        Position position,
        String timeZone,
        ShootWindow shootWindow,
        Solar solar,
        Weather weather,
        Environment environment,
        UnitBase unitBase,
        List<String> notes,
        List<String> attribution
) {

    public static final int VERSION = 1;

    /** @param geocoded whether the coordinates were looked up from the venue's address or name for this report */
    public record Position(BigDecimal latitude, BigDecimal longitude, boolean geocoded) {
    }

    /**
     * @param assumed   the scene has no shoot dates, so the coming week was used
     * @param truncated the scene's window was longer than one report covers; only its start is included
     */
    public record ShootWindow(LocalDate start, LocalDate end, boolean assumed, boolean truncated) {
    }

    public enum SectionStatus {
        OK,
        /** Some shoot days could not be looked up. */
        PARTIAL,
        /** The provider could not be reached; try again later. */
        UNAVAILABLE
    }

    /**
     * @param timeOfDay  the scene's time of day as the parser extracted it; null if not parsed
     * @param sceneLight how that was read; null if it names no natural light, and then no scene windows
     */
    public record Solar(String timeOfDay, SceneLight sceneLight, List<SolarDay> days) {
    }

    public record Weather(SectionStatus status, String message, List<WeatherDay> days) {
    }

    /**
     * One shoot day's weather. Metric throughout.
     *
     * @param referenceDate the day the values are from: the shoot date, or the same date in an earlier year
     *                      when {@code basis} is {@code PAST_YEAR}
     * @param warnings      what the conditions mean for the shoot, in plain words
     */
    public record WeatherDay(
            LocalDate date,
            Basis basis,
            LocalDate referenceDate,
            String summary,
            Double temperatureMaxC,
            Double temperatureMinC,
            Double precipitationMm,
            Integer precipitationProbabilityPercent,
            Double windSpeedMaxKmh,
            Double windGustsMaxKmh,
            Integer cloudCoverPercent,
            List<String> warnings
    ) {
    }

    /**
     * @param acousticSensitivity how much the scene's sound matters, from the parser; null if unknown
     * @param noiseRisk           the loudest nearby source, adjusted for that sensitivity; null if unavailable
     * @param noiseSources        loudest and nearest first
     * @param nearbyServices      the nearest few of each kind, grouped by kind
     */
    public record Environment(
            SectionStatus status,
            String message,
            AcousticSensitivity acousticSensitivity,
            NoiseLevel noiseRisk,
            List<NoiseSource> noiseSources,
            List<NearbyService> nearbyServices
    ) {
    }

    public enum NoiseLevel { LOW, MEDIUM, HIGH }

    /**
     * Where the trucks could park: open car parks, roadside bays and rest areas within {@code radiusMeters}, the
     * biggest first where the map says how big, then the nearest. Null in reports made before it was added.
     */
    public record UnitBase(SectionStatus status, String message, int radiusMeters, List<UnitBaseSite> sites) {
    }

    /**
     * @param kind             "Open car park", "Roadside bays (lay-by)", "Rest area" or "Car park"
     * @param capacity         spaces, where tagged
     * @param areaSquareMeters roughly, from its outline; null for a point
     */
    public record UnitBaseSite(String name, String kind, Integer capacity, Integer areaSquareMeters, int distanceMeters,
                               Double latitude, Double longitude) {
    }

    /** @param name null when the map has none */
    public record NoiseSource(PlaceKind kind, String name, int distanceMeters, NoiseLevel level, String advice) {
    }

    /** @param latitude null (with longitude) for places without a single position */
    public record NearbyService(PlaceKind kind, String name, int distanceMeters, Double latitude, Double longitude) {
    }
}
