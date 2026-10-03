package com.cinescout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The user's own part of a library venue (PUT): its name, tags (up to 10, repeats dropped) and notes. */
public record UpdateLibraryVenueRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 10) List<@NotBlank @Size(max = 40) String> tags,
        @Size(max = 4000) String notes
) {
}
