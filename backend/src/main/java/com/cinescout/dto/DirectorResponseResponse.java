package com.cinescout.dto;

import com.cinescout.domain.DirectorResponse;
import com.cinescout.domain.DirectorVerdict;

import java.time.Instant;
import java.util.UUID;

/** A guest's call on a venue, given through a director link. */
public record DirectorResponseResponse(UUID id, UUID locationId, String guestName, DirectorVerdict verdict, String comment,
                                       Instant createdAt, Instant updatedAt) {

    public static DirectorResponseResponse from(DirectorResponse response) {
        return new DirectorResponseResponse(response.getId(), response.getLocation().getId(), response.getGuestName(),
                response.getVerdict(), response.getComment(), response.getCreatedAt(), response.getUpdatedAt());
    }
}
