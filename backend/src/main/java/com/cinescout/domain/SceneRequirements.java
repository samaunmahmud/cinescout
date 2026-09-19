package com.cinescout.domain;

/**
 * The physical filming requirements extracted from a scene. This record is the
 * contract for the LLM's structured JSON output and is also returned to API
 * clients as-is.
 */
public record SceneRequirements(
        String settingType,
        String visualMood,
        String lightingNeeds,
        String timeOfDay,
        AcousticSensitivity acousticSensitivity,
        Integer estimatedCastAndCrewSize
) {
}
