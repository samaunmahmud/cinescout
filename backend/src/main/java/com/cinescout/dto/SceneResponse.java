package com.cinescout.dto;

import com.cinescout.domain.ParseStatus;
import com.cinescout.domain.Scene;
import com.cinescout.domain.SceneRequirements;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code requirements} is null until the scene has been parsed. The raw parser
 * output ({@code requirements_json}) is internal and not exposed.
 */
public record SceneResponse(
        UUID id,
        UUID projectId,
        Integer sceneNumber,
        String title,
        String sourceText,
        LocalDate shootDateStart,
        LocalDate shootDateEnd,
        ParseStatus parseStatus,
        SceneRequirements requirements,
        Instant parsedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static SceneResponse from(Scene scene) {
        return new SceneResponse(scene.getId(), scene.getProject().getId(), scene.getSceneNumber(),
                scene.getTitle(), scene.getSourceText(), scene.getShootDateStart(), scene.getShootDateEnd(),
                scene.getParseStatus(), scene.requirements(), scene.getParsedAt(),
                scene.getCreatedAt(), scene.getUpdatedAt());
    }
}
