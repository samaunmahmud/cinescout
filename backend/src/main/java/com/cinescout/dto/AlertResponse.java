package com.cinescout.dto;

import com.cinescout.domain.Alert;
import com.cinescout.domain.AlertKind;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An alert for the signed-in person.
 *
 * @param sceneId    the scene it is about; null when none
 * @param locationId the venue it is about; null when none
 * @param draftId    the outreach email it is about (FOLLOW_UP); null otherwise
 * @param payload    the facts its words are made of. WEATHER: scene, venue, day, rainChance, rainThreshold, windKmh, gustKmh,
 *                   windThreshold, reasons (RAIN, WIND) and covers (locationId, name, trigger). FOLLOW_UP: venue, scene,
 *                   subject, sentAt
 */
public record AlertResponse(
        UUID id,
        AlertKind kind,
        UUID projectId,
        String projectTitle,
        UUID sceneId,
        UUID locationId,
        UUID draftId,
        Map<String, Object> payload,
        boolean read,
        Instant createdAt
) {

    public static AlertResponse from(Alert alert) {
        return new AlertResponse(alert.getId(), alert.getKind(), alert.getProject().getId(), alert.getProject().getTitle(), alert.getSceneId(),
                alert.getLocationId(), alert.getDraftId(), alert.getPayload(), alert.getReadAt() != null, alert.getCreatedAt());
    }

    /** @param unread how many of the person's alerts are not read yet */
    public record Count(long unread) {
    }
}
