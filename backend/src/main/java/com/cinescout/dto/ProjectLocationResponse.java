package com.cinescout.dto;

import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.Scene;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A candidate location as one row of a project-wide list: what is needed to compare venues across scenes,
 * with the scene each is for. The page excerpt, warnings and logistics stay on the location itself.
 */
public record ProjectLocationResponse(
        UUID id,
        UUID sceneId,
        Integer sceneNumber,
        String sceneTitle,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String sourceUrl,
        Short fitScore,
        String fitReason,
        BookingFriction bookingFriction,
        LocationStatus status,
        String notes,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProjectLocationResponse from(Location location) {
        Scene scene = location.getScene();
        return new ProjectLocationResponse(location.getId(), scene.getId(), scene.getSceneNumber(), scene.getTitle(),
                location.getName(), location.getAddress(), location.getLatitude(), location.getLongitude(),
                location.getSourceUrl(), location.getFitScore(), location.getFitReason(), location.getBookingFriction(),
                location.getStatus(), location.getNotes(), location.getImageUrl(), location.getCreatedAt(), location.getUpdatedAt());
    }
}
