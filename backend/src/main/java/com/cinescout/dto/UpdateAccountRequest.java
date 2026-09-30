package com.cinescout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The account details a user can change themselves. The email is the login and stays as registered. */
public record UpdateAccountRequest(
        @NotBlank @Size(max = 100) String displayName
) {
}
