package com.cinescout.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

import java.time.LocalDate;

/** A scene's shoot window (PUT): a full replacement, so a null date clears it. */
public record ShootDatesRequest(
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
