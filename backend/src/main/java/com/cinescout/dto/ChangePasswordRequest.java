package com.cinescout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The new password has the limits registration sets; the current one proves the request is the owner's. */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {

    /** Keeps the passwords out of logs and exception messages. */
    @Override
    public String toString() {
        return "ChangePasswordRequest[<redacted>]";
    }
}
