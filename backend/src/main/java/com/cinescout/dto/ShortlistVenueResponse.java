package com.cinescout.dto;

import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A venue as a director link shows it: the picture, fit, booking note, warnings and position. {@code fitReason},
 * {@code notes} and {@code quote} stay null unless the link was made to show them. No contact details, source
 * excerpt or logistics: the link is for deciding, not for booking.
 */
public record ShortlistVenueResponse(
        UUID id,
        UUID sceneId,
        String sceneTitle,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl,
        Short fitScore,
        BookingFriction bookingFriction,
        String frictionNote,
        List<String> warnings,
        LocationStatus status,
        String fitReason,
        String notes,
        String quote,
        List<DirectorResponseResponse> responses
) {

    public static ShortlistVenueResponse from(Location location, boolean showPrivate, List<DirectorResponseResponse> responses) {
        List<String> warnings = location.getFootprintWarnings();
        return new ShortlistVenueResponse(location.getId(), location.getScene().getId(), location.getScene().getTitle(),
                location.getName(), location.getAddress(), location.getLatitude(), location.getLongitude(), location.getImageUrl(),
                location.getFitScore(), location.getBookingFriction(), location.getFrictionNote(),
                warnings == null ? List.of() : List.copyOf(warnings), location.getStatus(),
                showPrivate ? location.getFitReason() : null,
                showPrivate ? location.getNotes() : null,
                showPrivate ? location.getQuote() : null,
                responses);
    }
}
