package com.cinescout.dto;

import com.cinescout.domain.DirectorVerdict;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A guest's call on a venue, under the name they type. Answering again under the same name replaces it. */
public record DirectorResponseRequest(
        @NotBlank @Size(max = 60) String guestName,
        @NotNull DirectorVerdict verdict,
        @Size(max = 2000) String comment
) {
}
