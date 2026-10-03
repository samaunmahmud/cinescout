package com.cinescout.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** The member who becomes the project's owner; the current owner stays on as an EDITOR. */
public record TransferOwnershipRequest(@NotNull UUID userId) {
}
