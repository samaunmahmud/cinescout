package com.cinescout.dto;

import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.SceneCover;

import java.time.Instant;
import java.util.UUID;

/**
 * A backup venue for a scene.
 *
 * @param trigger     when to switch to it; null when not given
 * @param addedByName null once that account is gone
 */
public record CoverResponse(
        UUID id,
        UUID sceneId,
        UUID locationId,
        String venueName,
        String address,
        LocationStatus status,
        String trigger,
        String addedByName,
        Instant createdAt
) {

    public static CoverResponse from(SceneCover cover) {
        var venue = cover.getLocation();
        return new CoverResponse(cover.getId(), cover.getScene().getId(), venue.getId(), venue.getName(), venue.getAddress(), venue.getStatus(),
                cover.getTrigger(), cover.getAddedBy() == null ? null : cover.getAddedBy().getDisplayName(), cover.getCreatedAt());
    }
}
