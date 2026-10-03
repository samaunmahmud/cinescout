package com.cinescout.dto;

import com.cinescout.domain.OutreachReply;

import java.time.Instant;
import java.util.UUID;

/**
 * A reply to an outreach email. {@code source} INBOUND came in by mail to the draft's reply address, MANUAL was
 * pasted in by {@code recordedBy}. {@code text} is plain text, at most 20 KB; it is never sent to the AI.
 */
public record OutreachReplyResponse(UUID id, UUID draftId, String source, String fromAddress, String fromName, String subject,
                                    String text, Instant receivedAt, String recordedBy) {

    public static OutreachReplyResponse from(OutreachReply reply) {
        return new OutreachReplyResponse(reply.getId(), reply.getDraft().getId(), reply.getSource().name(), reply.getFromAddress(),
                reply.getFromName(), reply.getSubject(), reply.getBodyText(), reply.getReceivedAt(),
                reply.getRecordedBy() == null ? null : reply.getRecordedBy().getDisplayName());
    }
}
