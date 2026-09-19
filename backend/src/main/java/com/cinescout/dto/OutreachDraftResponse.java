package com.cinescout.dto;

import com.cinescout.domain.OutreachDraft;
import com.cinescout.domain.OutreachStatus;
import com.cinescout.domain.OutreachTone;

import java.time.Instant;
import java.util.UUID;

public record OutreachDraftResponse(
        UUID id,
        UUID locationId,
        String recipientName,
        String recipientEmail,
        String subject,
        String body,
        OutreachTone tone,
        String generatedBy,
        OutreachStatus status,
        Instant sentAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static OutreachDraftResponse from(OutreachDraft draft) {
        return new OutreachDraftResponse(draft.getId(), draft.getLocation().getId(),
                draft.getRecipientName(), draft.getRecipientEmail(), draft.getSubject(), draft.getBody(),
                draft.getTone(), draft.getGeneratedBy(), draft.getStatus(), draft.getSentAt(),
                draft.getCreatedAt(), draft.getUpdatedAt());
    }
}
