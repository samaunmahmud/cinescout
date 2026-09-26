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
 */
@Validated
@ConfigurationProperties("cinescout.scouting")
public record ScoutingProperties(
        @DefaultValue("4") @Min(1) @Max(16) int assessmentConcurrency,
        @DefaultValue("20s") @NotNull Duration placementBudget
) {
}
