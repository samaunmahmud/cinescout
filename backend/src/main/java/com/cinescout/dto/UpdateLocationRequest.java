package com.cinescout.dto;

import com.cinescout.domain.LocationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The user-owned workflow fields of a location (PUT): a null note clears it.
 * Everything else on a location is written by the search and assessment pipeline.
 */
public record UpdateLocationRequest(
        @NotNull LocationStatus status,
        @Size(max = 4000) String notes
) {
}
