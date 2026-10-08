package com.cinescout.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Ask a backup venue for a day the weather threatens: the day, and why ("Rain likely: 80%"). */
public record PlanBRequest(@NotNull LocalDate day, @Size(max = 200) String reason) {
}
