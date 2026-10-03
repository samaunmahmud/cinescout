package com.cinescout.dto;

import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.LibraryVenue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A venue in the user's library; {@code sourceLocationId} is the scouted venue it was saved from, if it still exists. */
public record LibraryVenueResponse(
        UUID id,
        UUID sourceLocationId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String sourceUrl,
        String imageUrl,
        BookingFriction bookingFriction,
        String frictionNote,
        List<String> footprintWarnings,
        String contactName,
        String contactEmail,
        String contactPhone,
        List<String> tags,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {

    public static LibraryVenueResponse from(LibraryVenue venue) {
        return new LibraryVenueResponse(venue.getId(), venue.getSourceLocationId(), venue.getName(), venue.getAddress(),
                venue.getLatitude(), venue.getLongitude(), venue.getSourceUrl(), venue.getImageUrl(), venue.getBookingFriction(),
                venue.getFrictionNote(), venue.getFootprintWarnings(), venue.getContactName(), venue.getContactEmail(),
                venue.getContactPhone(), venue.getTags(), venue.getNotes(), venue.getCreatedAt(), venue.getUpdatedAt());
    }
}
