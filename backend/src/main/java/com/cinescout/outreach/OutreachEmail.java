package com.cinescout.outreach;

import jakarta.validation.constraints.NotBlank;

/**
 * The LLM's draft of an email to a venue owner. Like the other AI-facing records this is the contract
 * for the model's structured JSON output, validated before anything is stored.
 *
 * @param subject a single-line subject
 * @param body    the email in plain text, greeting and sign-off included, without the subject
 */
public record OutreachEmail(
        @NotBlank String subject,
        @NotBlank String body
) {
}
