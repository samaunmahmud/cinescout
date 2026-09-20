package com.cinescout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        // Where scouting searches, e.g. "Brooklyn, New York"; may be set later.
        @Size(max = 200) String locationArea
) {
}
