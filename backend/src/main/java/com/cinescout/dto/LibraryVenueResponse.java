package com.cinescout.dto;

import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.LibraryVenue;
import com.cinescout.logistics.GeoPoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A venue in the user's library; {@code sourceLocationId} is the scouted venue it was saved from, if it still exists.
 * {@code distanceKm} is how far it is from the point the list was sorted by, when it was, and the venue has a position.
 */
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
        Instant updatedAt,
        Double distanceKm
) {
    public static LibraryVenueResponse from(LibraryVenue venue) {
        return from(venue, null);
    }

    /** With the distance from {@code from}, to 0.1 km, when both are known. */
    public static LibraryVenueResponse from(LibraryVenue venue, GeoPoint from) {
        Double distanceKm = from == null || venue.getLatitude() == null || venue.getLongitude() == null ? null
                : Math.round(from.distanceTo(new GeoPoint(venue.getLatitude().doubleValue(), venue.getLongitude().doubleValue())) / 100) / 10.0;
        return new LibraryVenueResponse(venue.getId(), venue.getSourceLocationId(), venue.getName(), venue.getAddress(),
                venue.getLatitude(), venue.getLongitude(), venue.getSourceUrl(), venue.getImageUrl(), venue.getBookingFriction(),
                venue.getFrictionNote(), venue.getFootprintWarnings(), venue.getContactName(), venue.getContactEmail(),
                venue.getContactPhone(), venue.getTags(), venue.getNotes(), venue.getCreatedAt(), venue.getUpdatedAt(), distanceKm);
    }
}
