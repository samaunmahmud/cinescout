package com.cinescout.dto;

import com.cinescout.domain.AvailabilityState;
import com.cinescout.domain.VenueAvailability;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** @param setByName who set it last; null once that account is gone */
public record AvailabilityResponse(
        UUID id,
        UUID locationId,
        LocalDate day,
        AvailabilityState state,
        LocalDate holdExpiresOn,
        String note,
        String setByName,
        Instant updatedAt
) {

    public static AvailabilityResponse from(VenueAvailability day) {
        return new AvailabilityResponse(day.getId(), day.getLocation().getId(), day.getDay(), day.getState(), day.getHoldExpiresOn(),
                day.getNote(), day.getSetBy() == null ? null : day.getSetBy().getDisplayName(), day.getUpdatedAt());
    }
}
