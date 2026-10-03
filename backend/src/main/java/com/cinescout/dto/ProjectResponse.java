package com.cinescout.dto;

import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * @param sceneCount          how many scenes the project has
 * @param confirmedSceneCount how many of them have a confirmed location: with {@code sceneCount}, how far the
 *                            scouting has come, at a glance
 * @param posterImageUrl      a picture of one of the project's venues (a confirmed one if it has any), for its poster;
 *                            null while none has a picture
 * @param role                the asking user's role on the project, so the app can offer only what they may do
 */
public record ProjectResponse(
        UUID id,
        String title,
        String description,
        String locationArea,
        ProjectStatus status,
        long sceneCount,
        long confirmedSceneCount,
        String posterImageUrl,
        ProjectRole role,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProjectResponse from(Project project, long sceneCount, long confirmedSceneCount, String posterImageUrl,
                                       ProjectRole role) {
        return new ProjectResponse(project.getId(), project.getTitle(), project.getDescription(),
                project.getLocationArea(), project.getStatus(), sceneCount, confirmedSceneCount, posterImageUrl, role,
                project.getCreatedAt(), project.getUpdatedAt());
    }
}
