package com.cinescout.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Who to talk to at a venue and what they ask for the shoot (PUT): a full replacement, so a null or blank field
 * clears it. The quote is free text ("$425 an hour, four hour minimum").
 */
public record UpdateContactRequest(
        @Size(max = 200) String name,
        @Email @Size(max = 254) String email,
        // Digits with the punctuation people write phone numbers with; no letters, so nothing else is stored here.
        @Size(max = 40) @Pattern(regexp = "[0-9+()./ xX-]*", message = "must be a phone number") String phone,
        @Size(max = 300) String quote
) {
}
