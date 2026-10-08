package com.cinescout.dto;

import com.cinescout.domain.BudgetCategory;
import com.cinescout.domain.BudgetStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A budget line to add or replace. {@code status} defaults to ESTIMATE; {@code locationId}, when given, must be one of
 * the project's venues.
 */
public record BudgetItemRequest(
        @NotNull BudgetCategory category,
        @NotBlank @Size(max = 200) String label,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        BudgetStatus status,
        @Size(max = 1000) String note,
        UUID locationId
) {
}
