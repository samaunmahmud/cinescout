package com.cinescout.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** A scouted venue to keep in the user's library. */
public record SaveToLibraryRequest(@NotNull UUID locationId) {
}
