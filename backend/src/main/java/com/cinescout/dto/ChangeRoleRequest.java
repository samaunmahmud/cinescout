package com.cinescout.dto;

import com.cinescout.domain.ProjectRole;
import jakarta.validation.constraints.NotNull;

/** A member's new role, EDITOR or VIEWER; ownership changes hands by a transfer instead. */
public record ChangeRoleRequest(@NotNull ProjectRole role) {
}
