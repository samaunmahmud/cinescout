package com.cinescout.jobs;

import java.time.Instant;

/**
 * What one run of a job did.
 *
 * @param affected how many things it changed (emails flagged, alerts made); 0 when there was nothing to do
 */
public record JobRun(String job, int affected, Instant finishedAt) {
}
