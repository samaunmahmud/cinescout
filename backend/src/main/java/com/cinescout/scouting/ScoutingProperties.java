package com.cinescout.scouting;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param assessmentConcurrency how many venues are assessed by the LLM at once. Bounded so one scouting
 *                              run cannot flood the model's rate limit; each venue is a separate call
 */
@Validated
@ConfigurationProperties("cinescout.scouting")
public record ScoutingProperties(
        @DefaultValue("4") @Min(1) @Max(16) int assessmentConcurrency
) {
}
