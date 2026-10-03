package com.cinescout.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A scene's shoot window (PUT): a full replacement, so a null date or time clears it.
 *
 * @param callTime when the crew is called on each shoot day ("07:30")
 * @param wrapTime when the scene wraps; at or before the call time it is the next morning (a night shoot)
 */
public record ShootDatesRequest(
        LocalDate shootDateStart,
        LocalDate shootDateEnd,
        LocalTime callTime,
        LocalTime wrapTime
) {

    /** Dates alone, no times. */
    public ShootDatesRequest(LocalDate shootDateStart, LocalDate shootDateEnd) {
        this(shootDateStart, shootDateEnd, null, null);
    }

    /** Mirrors the ck_scenes_shoot_window constraint so clients get a 400, not a database error. */
    @JsonIgnore
    @AssertTrue(message = "shootDateEnd must not be before shootDateStart")
    public boolean isShootWindowValid() {
        return shootDateStart == null || shootDateEnd == null || !shootDateEnd.isBefore(shootDateStart);
    }
}
