package com.cinescout.dto;

import com.cinescout.domain.LocationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The user-owned workflow fields of a location (PUT): a null note clears it.
 * Everything else on a location is written by the search and assessment pipeline.
 * {@code rejectionReason} says why the crew passed on it: kept only while the status is REJECTED, and left as it
 * was when null (so saving the notes of a rejected venue keeps its reason).
 */
public record UpdateLocationRequest(
        @NotNull LocationStatus status,
        @Size(max = 4000) String notes,
        @Size(max = 300) String rejectionReason
) {

    public UpdateLocationRequest(LocationStatus status, String notes) {
        this(status, notes, null);
    }
}
