package com.cinescout.dto;

import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code logistics} is the cached Module B output, a {@code LogisticsReport} passed through exactly as it
 * was stored (so its local times keep their offsets). It is null until logistics have been worked out.
 * The {@code contact...} fields are who to talk to at the venue, as the user entered them.
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
        String contactName,
        String contactEmail,
        String contactPhone,
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
                location.getStatus(), location.getNotes(),
                location.getContactName(), location.getContactEmail(), location.getContactPhone(),
                location.getCreatedAt(), location.getUpdatedAt());
    }
}
