package com.cinescout.dto;

import jakarta.validation.constraints.NotNull;

/** Moves a shot one place earlier or later in the shooting order. */
public record ShotMoveRequest(@NotNull Direction direction) {

    public enum Direction { EARLIER, LATER }
}
