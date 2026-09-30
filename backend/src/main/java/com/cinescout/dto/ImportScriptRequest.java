package com.cinescout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A screenplay, or part of one, as plain text, to be cut into scenes at its scene headings. */
public record ImportScriptRequest(
        @NotBlank @Size(max = MAX_SCRIPT_LENGTH) String script
) {

    /** A feature-length screenplay is around 200,000 characters. */
    public static final int MAX_SCRIPT_LENGTH = 500_000;
}
