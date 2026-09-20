package com.cinescout.dto;

import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String title,
        String description,
        String locationArea,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getTitle(), project.getDescription(),
                project.getLocationArea(), project.getStatus(), project.getCreatedAt(), project.getUpdatedAt());
    }
}
