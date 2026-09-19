package com.cinescout.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A venue the user found themselves, added to a scene by hand. Venues found by
 * the search pipeline are created by the service and never go through this payload.
 */
public record CreateLocationRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String address,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        // http(s) only: the URL is rendered as a link, so no javascript: or data: schemes.
        @Size(max = 2048) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String sourceUrl,
        @Size(max = 4000) String notes
) {

    @JsonIgnore
    @AssertTrue(message = "latitude and longitude must be given together")
    public boolean isCoordinatePairComplete() {
        return (latitude == null) == (longitude == null);
    }
}
