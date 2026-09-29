package com.cinescout.outreach;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The LLM's draft of an email to a venue owner. Like the other AI-facing records this is the contract
 * for the model's structured JSON output, validated before anything is stored.
 *
 * <p>The email comes in parts rather than as one text, because models asked for "paragraphs" inside a JSON
 * string tend to return a single block; {@link #body()} lays the parts out with a blank line between each.
 *
 * @param subject    a single-line subject
 * @param greeting   the opening line alone, e.g. "Hello Maria,"
 * @param paragraphs the message, a few short paragraphs; blank ones are dropped
 * @param signOff    the closing and the sender's name, e.g. "Best wishes,\nSam"
 */
public record OutreachEmail(
        @NotBlank String subject,
        @NotBlank String greeting,
        @NotEmpty List<@NotBlank String> paragraphs,
        @NotBlank String signOff
) {

    public OutreachEmail {
        paragraphs = paragraphs == null ? List.of() : paragraphs.stream()
                .filter(p -> p != null && !p.isBlank())
                .toList();
    }

    /** The email as plain text, greeting and sign-off included, without the subject. */
    public String body() {
        return Stream.concat(Stream.concat(Stream.of(greeting), paragraphs.stream()), Stream.of(signOff))
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }
}
