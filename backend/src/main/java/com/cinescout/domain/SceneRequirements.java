package com.cinescout.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * The physical filming requirements extracted from a scene. This record is the
 * contract for the LLM's structured JSON output and is also returned to API
 * clients as-is.
 *
 * <p>Only the setting type is mandatory: it is what venue search runs on, so an
 * extraction without one is rejected as unusable (and retried) rather than stored.
 * The crew size mirrors the {@code estimated_crew_size >= 0} database constraint.
 */
public record SceneRequirements(
        @NotBlank String settingType,
        String visualMood,
        String lightingNeeds,
        String timeOfDay,
        AcousticSensitivity acousticSensitivity,
        @PositiveOrZero Integer estimatedCastAndCrewSize
) {
}
