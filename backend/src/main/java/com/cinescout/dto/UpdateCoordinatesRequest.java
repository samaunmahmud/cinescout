package com.cinescout.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Where a venue is (PUT), in decimal degrees; the database keeps six decimal places (about 10 cm). */
public record UpdateCoordinatesRequest(
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 6) BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude
) {
}
