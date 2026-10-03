package com.cinescout.jobs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param secret    the shared secret a caller of {@code /api/internal/jobs/*} sends in {@code X-Job-Secret}; empty
 *                  turns that endpoint off (404), and only the in-app timer runs the jobs
 * @param scheduler whether the app also runs the jobs on its own timer, as a fallback while it is awake (each job's
 *                  cron is its own property, e.g. {@code cinescout.jobs.follow-ups})
 */
@ConfigurationProperties("cinescout.jobs")
public record JobsProperties(
        String secret,
        @DefaultValue("true") boolean scheduler
) {

    public boolean endpointEnabled() {
        return secret != null && !secret.isBlank();
    }
}
