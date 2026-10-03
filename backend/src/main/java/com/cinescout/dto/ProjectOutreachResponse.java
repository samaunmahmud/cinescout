package com.cinescout.dto;

import com.cinescout.domain.Location;
import com.cinescout.domain.OutreachDraft;
import com.cinescout.domain.OutreachStatus;
import com.cinescout.domain.OutreachTone;
import com.cinescout.domain.Scene;

import java.time.Instant;
import java.util.UUID;

/**
 * An outreach email as one row of a project-wide list: who was written to about which venue, for which
 * scene, and how far it got, and whether it waits on a follow-up. The email's text stays on the draft itself.
 */
public record ProjectOutreachResponse(
        UUID id,
        UUID locationId,
        String locationName,
        UUID sceneId,
        Integer sceneNumber,
        String sceneTitle,
        String recipientName,
        String recipientEmail,
        String subject,
        OutreachTone tone,
        OutreachStatus status,
        Instant sentAt,
        Instant followUpFlaggedAt,
        UUID followUpOfId,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProjectOutreachResponse from(OutreachDraft draft) {
        Location location = draft.getLocation();
        Scene scene = location.getScene();
        return new ProjectOutreachResponse(draft.getId(), location.getId(), location.getName(),
                scene.getId(), scene.getSceneNumber(), scene.getTitle(),
                draft.getRecipientName(), draft.getRecipientEmail(), draft.getSubject(), draft.getTone(),
                draft.getStatus(), draft.getSentAt(), draft.getFollowUpFlaggedAt(),
                draft.getFollowUpOf() == null ? null : draft.getFollowUpOf().getId(), draft.getCreatedAt(), draft.getUpdatedAt());
    }
}
