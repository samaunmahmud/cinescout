package com.cinescout.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param locationId one of the scene's own candidates (a venue from the library is added to the scene first)
 * @param trigger    when to switch to it, in words ("if rain > 60%"); optional
 */
public record CoverRequest(@NotNull UUID locationId, @Size(max = 200) String trigger) {
}
