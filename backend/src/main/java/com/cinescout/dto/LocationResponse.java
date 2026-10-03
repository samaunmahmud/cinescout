package com.cinescout.dto;

import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.RecceEntry;
import com.cinescout.files.FileLinks;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code logistics} is the cached Module B output, a {@code LogisticsReport} passed through exactly as it
 * was stored (so its local times keep their offsets). It is null until logistics have been worked out.
 * The {@code contact...} fields are who to talk to at the venue, and {@code quote} what it asks for the shoot, as the user
 * entered them. {@code rejectionReason} is why the crew passed on it, while it is REJECTED. {@code imageUrl} is the
 * picture the venue shows: its chosen recce photo ({@code coverPhotoId}, as a short-lived link) or else the one its page
 * offers; {@code imageCheckedAt} is null until the page has been looked at. {@code recce} is the tech recce checklist:
 * answers by field name, each with who gave it and when.
 */
public record LocationResponse(
        UUID id,
        UUID sceneId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String sourceUrl,
        String sourceProvider,
        String sourceExcerpt,
        String fitReason,
        Short fitScore,
        BookingFriction bookingFriction,
        String frictionNote,
        List<String> footprintWarnings,
        JsonNode logistics,
        Instant logisticsFetchedAt,
        LocationStatus status,
        String notes,
        String rejectionReason,
        String contactName,
        String contactEmail,
        String contactPhone,
        String quote,
        String imageUrl,
        Instant imageCheckedAt,
        UUID coverPhotoId,
        Map<String, RecceEntry> recce,
        Instant createdAt,
        Instant updatedAt
) {

    public static LocationResponse from(Location location) {
        List<String> warnings = location.getFootprintWarnings();
        return new LocationResponse(location.getId(), location.getScene().getId(), location.getName(),
                location.getAddress(), location.getLatitude(), location.getLongitude(),
                location.getSourceUrl(), location.getSourceProvider(), location.getSourceExcerpt(),
                location.getFitReason(), location.getFitScore(), location.getBookingFriction(),
                location.getFrictionNote(), warnings == null ? List.of() : List.copyOf(warnings),
                location.getLogisticsJson(), location.getLogisticsFetchedAt(),
                location.getStatus(), location.getNotes(), location.getRejectionReason(),
                location.getContactName(), location.getContactEmail(), location.getContactPhone(), location.getQuote(),
                location.getCoverPhotoId() != null ? FileLinks.current().photo(location.getCoverPhotoId(), "full") : location.getImageUrl(),
                location.getImageCheckedAt(), location.getCoverPhotoId(), location.getRecce(),
                location.getCreatedAt(), location.getUpdatedAt());
    }
}
