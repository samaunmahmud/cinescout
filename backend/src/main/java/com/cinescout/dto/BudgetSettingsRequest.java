package com.cinescout.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** The production's total budget (null for none yet) and the ISO 4217 currency every line is in. */
public record BudgetSettingsRequest(
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal total,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter currency code such as GBP") String currency
) {
}
