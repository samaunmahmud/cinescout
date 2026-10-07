package com.cinescout.jobs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret    the shared secret a caller of {@code /api/internal/jobs/*} sends in {@code X-Job-Secret}; empty
 *                  turns that endpoint off (404), and only the in-app timer runs the jobs
 * @param scheduler whether the app also runs the jobs on its own timer, as a fallback while it is awake (each job's
 *                  cron is its own property, e.g. {@code cinescout.jobs.follow-ups})
 * @param weatherVenues how many venues the weather watch fetches a forecast for in one run, the earliest shoot first
 */
@Validated
@ConfigurationProperties("cinescout.jobs")
public record JobsProperties(
        String secret,
        @DefaultValue("true") boolean scheduler,
        @DefaultValue("40") @Min(1) @Max(500) int weatherVenues
) {

    public boolean endpointEnabled() {
        return secret != null && !secret.isBlank();
    }
}
