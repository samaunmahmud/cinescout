package com.cinescout.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // bcrypt only reads the first 72 bytes, so longer passwords add nothing.
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String displayName
) {

    /** Keeps the password out of logs and exception messages. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=<redacted>, displayName=" + displayName + "]";
    }
}
