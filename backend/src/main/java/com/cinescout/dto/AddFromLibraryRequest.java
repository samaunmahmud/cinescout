package com.cinescout.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** A venue from the user's library to add to a scene. */
public record AddFromLibraryRequest(@NotNull UUID libraryVenueId) {
}
