package com.cinescout.dto;

import com.cinescout.domain.ScoutFilters;
import jakarta.validation.Valid;

/** Options for one scouting run: {@code filters} replace the project's own for this run; null keeps them. */
public record ScoutRequest(@Valid ScoutFilters filters) {
}
