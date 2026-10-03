package com.cinescout.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * A reply pasted in by hand, when no inbound provider is set up, or the venue answered another way. Every field is
 * optional: sending none just marks the draft replied.
 */
public record ManualReplyRequest(
        @Email @Size(max = 320) String fromAddress,
        @Size(max = 200) String fromName,
        @Size(max = 500) String subject,
        @Size(max = 20480) String text,
        @PastOrPresent Instant receivedAt
) {
}
