package com.cinescout.dto;

import com.cinescout.domain.User;
import com.cinescout.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

/** Public view of an account. Deliberately has no password hash. */
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.getCreatedAt());
    }
}
