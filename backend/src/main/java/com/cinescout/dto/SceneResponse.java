package com.cinescout.dto;

import com.cinescout.domain.ParseStatus;
import com.cinescout.domain.Scene;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.script.ScriptCharacters;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code requirements} is null until the scene has been parsed. The raw parser
 * output ({@code requirements_json}) is internal and not exposed. {@code characters} are the speaking parts,
 * read from the script's format (see {@code ScriptCharacters}).
 */
public record SceneResponse(
        UUID id,
        UUID projectId,
        Integer sceneNumber,
        String title,
        String sourceText,
        LocalDate shootDateStart,
        LocalDate shootDateEnd,
        LocalTime callTime,
        LocalTime wrapTime,
        ParseStatus parseStatus,
        SceneRequirements requirements,
        List<String> characters,
        Instant parsedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static SceneResponse from(Scene scene) {
        return new SceneResponse(scene.getId(), scene.getProject().getId(), scene.getSceneNumber(),
                scene.getTitle(), scene.getSourceText(), scene.getShootDateStart(), scene.getShootDateEnd(),
                scene.getCallTime(), scene.getWrapTime(), scene.getParseStatus(), scene.requirements(), ScriptCharacters.in(scene.getSourceText()), scene.getParsedAt(),
                scene.getCreatedAt(), scene.getUpdatedAt());
    }
}
