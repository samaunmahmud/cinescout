package com.cinescout.dto;

import com.cinescout.domain.OutreachDraft;
import com.cinescout.domain.OutreachStatus;
import com.cinescout.domain.OutreachTone;
import com.cinescout.mail.ReplyAddresses;

import java.time.Instant;
import java.util.UUID;

/**
 * @param followUpFlaggedAt when the email was found unanswered for longer than the project allows; null while it is not
 *                          waiting on a follow-up
 * @param followUpOfId      the earlier email this one chases; null for a first email
 */
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
        String replyTo,
        Instant followUpFlaggedAt,
        UUID followUpOfId,
        Instant createdAt,
        Instant updatedAt
) {

    public static OutreachDraftResponse from(OutreachDraft draft) {
        return new OutreachDraftResponse(draft.getId(), draft.getLocation().getId(),
                draft.getRecipientName(), draft.getRecipientEmail(), draft.getSubject(), draft.getBody(),
                draft.getTone(), draft.getGeneratedBy(), draft.getStatus(), draft.getSentAt(),
                ReplyAddresses.current().address(draft.getReplyToken()),
                draft.getFollowUpFlaggedAt(), draft.getFollowUpOf() == null ? null : draft.getFollowUpOf().getId(),
                draft.getCreatedAt(), draft.getUpdatedAt());
    }
}
