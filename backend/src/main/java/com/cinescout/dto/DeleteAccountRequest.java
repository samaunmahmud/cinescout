package com.cinescout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Deleting an account cannot be undone, so it takes the password again, not just a login. */
public record DeleteAccountRequest(
        @NotBlank @Size(max = 72) String password
) {

    @Override
    public String toString() {
        return "DeleteAccountRequest[<redacted>]";
    }
}
