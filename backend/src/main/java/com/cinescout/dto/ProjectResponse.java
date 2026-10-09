package com.cinescout.dto;

import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.ProjectStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param sceneCount          how many scenes the project has
 * @param confirmedSceneCount how many of them have a confirmed location: with {@code sceneCount}, how far the
 *                            scouting has come, at a glance
 * @param posterImageUrl      a picture of one of the project's venues (a confirmed one if it has any), for its poster;
 *                            null while none has a picture
 * @param role                the asking user's role on the project, so the app can offer only what they may do
 * @param followUpCount       how many sent emails have gone unanswered too long and wait on a follow-up
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
        long followUpCount,
        Instant createdAt,
        Instant updatedAt,
        List<String> crew
) {

    public ProjectResponse {
        crew = crew == null ? List.of() : List.copyOf(crew);
    }

    public static ProjectResponse from(Project project, long sceneCount, long confirmedSceneCount, String posterImageUrl,
                                       ProjectRole role, long followUpCount) {
        return from(project, sceneCount, confirmedSceneCount, posterImageUrl, role, followUpCount, List.of());
    }

    /** @param crew the names of the people on it, the owner first, for avatars; at most {@link #MAX_CREW} */
    public static ProjectResponse from(Project project, long sceneCount, long confirmedSceneCount, String posterImageUrl,
                                       ProjectRole role, long followUpCount, List<String> crew) {
        return new ProjectResponse(project.getId(), project.getTitle(), project.getDescription(),
                project.getLocationArea(), project.getStatus(), sceneCount, confirmedSceneCount, posterImageUrl, role, followUpCount,
                project.getCreatedAt(), project.getUpdatedAt(), crew);
    }

    /** How many names {@code crew} carries. */
    public static final int MAX_CREW = 6;
}
