package com.cinescout.dto;

import com.cinescout.domain.ShotSize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.UUID;

/**
 * A shot to add or replace. {@code cameraBearing} is which way the camera faces, in degrees from north;
 * {@code locationId}, when given, is one of the scene's venues (otherwise its confirmed venue is meant).
 */
public record ShotRequest(
        @NotBlank @Size(max = 500) String description,
        ShotSize size,
        @Min(0) @Max(359) Integer cameraBearing,
        LocalTime plannedTime,
        Boolean done,
        UUID locationId
) {
}
