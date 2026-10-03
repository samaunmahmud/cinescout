package com.cinescout.dto;

import com.cinescout.domain.Project;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A project's working settings, read by the whole crew and set by editors and the owner (PUT, full replacement).
 *
 * @param followUpDays how many days a sent outreach email may go unanswered before it is flagged for a follow-up
 */
public record ProjectSettings(
        @NotNull @Min(1) @Max(60) Integer followUpDays
) {

    public static ProjectSettings from(Project project) {
        return new ProjectSettings(project.getFollowUpDays());
    }
}
