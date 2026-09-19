package com.cinescout.dto;

import com.cinescout.domain.OutreachStatus;
import com.cinescout.domain.OutreachTone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full replacement (PUT) of a draft's editable fields; null recipient fields clear them. */
public record UpdateOutreachRequest(
        @NotBlank @Size(max = 300) String subject,
        @NotBlank @Size(max = 10_000) String body,
        @NotNull OutreachTone tone,
        @NotNull OutreachStatus status,
        @Size(max = 200) String recipientName,
        @Email @Size(max = 254) String recipientEmail
) {
}
