package com.cinescout.ai;

import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.SceneRequirements;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The model's judgement of one search result against a scene: the contract for its structured JSON output, turned
 * into a {@link LocationAssessment} by {@link #toAssessment}. The model is not asked for a score. Asked for one,
 * Llama gave nearly every venue 60; asked instead what kind of place it is and, requirement by requirement, what the
 * page shows, it answers in categories, and {@link #fitScore} turns those into a score that spreads venues out the
 * same way every time.
 *
 * @param singleVenue       whether the page is about one specific venue; see {@link LocationAssessment#singleVenue()}
 * @param venueName         the venue's own name; optional
 * @param address           the venue's street address when the excerpt states it; optional, never guessed
 * @param outsideSearchArea true when the page shows the venue is in another city or region
 * @param setting           how close the kind of place is to the setting the scene needs
 * @param mood              what the page shows about the scene's visual mood
 * @param lighting          ... about its lighting needs
 * @param timeOfDay         ... about shooting at its time of day (opening hours, daylight, the look at that hour)
 * @param sound             ... about how quiet it is, for a scene that needs clean sound
 * @param capacity          ... about room for the cast and crew
 * @param fitReason         one or two sentences on what in the excerpt supports the verdict
 * @param bookingFriction   who has to say yes
 * @param frictionNote      what that means in practice; optional
 * @param footprintWarnings short warnings about the crew's footprint; null or empty when there are none
 * @param listedVenues      for a page that is not one venue, the venues it names; see {@link LocationAssessment}
 */
public record VenueVerdict(
        @NotNull Boolean singleVenue,
        String venueName,
        String address,
        @NotNull Boolean outsideSearchArea,
        @NotNull SettingMatch setting,
        @NotNull Evidence mood,
        @NotNull Evidence lighting,
        @NotNull Evidence timeOfDay,
        @NotNull Evidence sound,
        @NotNull Evidence capacity,
        @NotBlank String fitReason,
        @NotNull BookingFriction bookingFriction,
        String frictionNote,
        List<@NotBlank String> footprintWarnings,
        List<String> listedVenues
) {

    /** How close the kind of place is to the setting the scene names. */
    public enum SettingMatch {
        /** The kind of place the scene needs. */
        EXACT,
        /** A near kind that passes on camera with light dressing (a cafe for a diner). */
        CLOSE,
        /** A different kind of place that would need heavy dressing to pass (a loft for a diner). */
        DRESSABLE,
        /** Could not pass for it. */
        UNSUITABLE
    }

    /** What the page shows about one requirement. */
    public enum Evidence {
        MEETS, FAILS, UNKNOWN
    }

    static final int EXACT_BASE = 70;
    static final int CLOSE_BASE = 55;
    static final int DRESSABLE_BASE = 35;
    static final int UNSUITABLE_BASE = 15;
    /** Five requirements met take the right kind of place from 70 to 100. */
    static final int MEETS = 6;
    static final int FAILS = -8;
    /** Too small for the crew, or loud for a dialogue scene, is harder to work around than the wrong mood. */
    static final int FAILS_HARD = -12;

    /**
     * The score, 0 to 100. Zero means unusable (not one venue, or in another place) and is left out of a scouting run;
     * anything else is at least 1. Requirements the scene does not state are not counted whatever the model says
     * about them, and neither is sound when the scene is not sensitive to it.
     */
    public int fitScore(SceneRequirements requirements) {
        if (!singleVenue || outsideSearchArea) {
            return 0;
        }
        int score = switch (setting) {
            case EXACT -> EXACT_BASE;
            case CLOSE -> CLOSE_BASE;
            case DRESSABLE -> DRESSABLE_BASE;
            case UNSUITABLE -> UNSUITABLE_BASE;
        };
        score += points(mood, stated(requirements.visualMood()), FAILS);
        score += points(lighting, stated(requirements.lightingNeeds()), FAILS);
        score += points(timeOfDay, stated(requirements.timeOfDay()), FAILS);
        score += points(sound, requirements.acousticSensitivity() == AcousticSensitivity.MEDIUM
                || requirements.acousticSensitivity() == AcousticSensitivity.HIGH, FAILS_HARD);
        score += points(capacity, requirements.estimatedCastAndCrewSize() != null, FAILS_HARD);
        return Math.clamp(score, 1, 100);
    }

    /** The assessment the rest of scouting works with, scored against {@code requirements}. */
    public LocationAssessment toAssessment(SceneRequirements requirements) {
        return new LocationAssessment(singleVenue, fitScore(requirements), fitReason, bookingFriction, frictionNote,
                footprintWarnings, venueName, address, listedVenues);
    }

    private static int points(Evidence evidence, boolean counted, int whenFails) {
        if (!counted) {
            return 0;
        }
        return switch (evidence) {
            case MEETS -> MEETS;
            case FAILS -> whenFails;
            case UNKNOWN -> 0;
        };
    }

    private static boolean stated(String requirement) {
        return requirement != null && !requirement.isBlank();
    }
}
