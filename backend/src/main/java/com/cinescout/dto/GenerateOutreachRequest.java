package com.cinescout.dto;

import com.cinescout.domain.OutreachTone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Asks the generator to draft an email to a venue owner. All fields are optional. */
public record GenerateOutreachRequest(
        OutreachTone tone,
        @Size(max = 200) String recipientName,
        @Email @Size(max = 254) String recipientEmail,
        // Free text the generator should work in (e.g. "we can shoot on a weekday").
        @Size(max = 2000) String additionalContext
) {

    public GenerateOutreachRequest {
        if (tone == null) {
            tone = OutreachTone.PROFESSIONAL;
        }
    }
}
