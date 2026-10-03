package com.cinescout.dto;

import com.cinescout.domain.ProjectRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Someone to bring onto the crew, by their email address, as an EDITOR or a VIEWER. */
public record AddMemberRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull ProjectRole role
) {
}
