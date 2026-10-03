package com.cinescout.jobs;

import reactor.core.publisher.Mono;

/**
 * Work done on a timetable rather than at a user's request. Render's free tier sleeps when idle, so an in-process
 * timer alone cannot be trusted: each job can also be run by name through {@code POST /api/internal/jobs/{name}},
 * which a scheduled GitHub Actions workflow calls with the shared secret. Running a job twice must be harmless.
 */
public interface Job {

    /** The name in the job's URL, e.g. {@code follow-ups}. */
    String name();

    Mono<JobRun> run();
}
