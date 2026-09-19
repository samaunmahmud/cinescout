package com.cinescout.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Payload for creating a scene and for replacing its editable fields (PUT). */
public record SceneRequest(
        @Positive Integer sceneNumber,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 20_000) String sourceText,
        LocalDate shootDateStart,
        LocalDate shootDateEnd
) {

    /** Mirrors the ck_scenes_shoot_window constraint so clients get a 400, not a database error. */
    @JsonIgnore
    @AssertTrue(message = "shootDateEnd must not be before shootDateStart")
    public boolean isShootWindowValid() {
        return shootDateStart == null || shootDateEnd == null || !shootDateEnd.isBefore(shootDateStart);
    }
}
