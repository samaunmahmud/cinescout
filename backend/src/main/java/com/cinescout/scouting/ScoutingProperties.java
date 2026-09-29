package com.cinescout.scouting;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param assessmentConcurrency how many venues are assessed by the LLM at once. Bounded so one scouting
 *                              run cannot flood the model's rate limit; each venue is a separate call
 * @param placementBudget       how long a run may spend finding its new venues on the map (one a second
 *                              on the public geocoder); venues not reached are saved without coordinates.
 *                              Zero turns placement off
 * @param followUpVenues        how many venues named on directory pages a run looks up by name (one small search
 *                              and up to two assessments each). Zero turns follow-up searches off
 */
@Validated
@ConfigurationProperties("cinescout.scouting")
public record ScoutingProperties(
        @DefaultValue("4") @Min(1) @Max(16) int assessmentConcurrency,
        @DefaultValue("20s") @NotNull Duration placementBudget,
        @DefaultValue("5") @Min(0) @Max(10) int followUpVenues
) {
}
