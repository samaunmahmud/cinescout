package com.cinescout.dto;

import com.cinescout.domain.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full replacement (PUT): a null description or location area clears it. */
public record UpdateProjectRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Size(max = 200) String locationArea,
        @NotNull ProjectStatus status
) {
}
