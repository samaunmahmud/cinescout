package com.cinescout.dto;

import com.cinescout.domain.ProjectInvite;
import com.cinescout.domain.ProjectRole;

import java.time.Instant;
import java.util.UUID;

/**
 * An invitation that has not been taken up yet.
 *
 * @param token the secret for the link ({@code /invite/{token}} in the web app); only in the answer that creates the
 *              invite, since only its hash is kept. Null everywhere else
 */
public record InviteResponse(UUID id, String email, ProjectRole role, Instant createdAt, Instant expiresAt, String token) {

    public static InviteResponse from(ProjectInvite invite, String token) {
        return new InviteResponse(invite.getId(), invite.getEmail(), invite.getRole(), invite.getCreatedAt(), invite.getExpiresAt(), token);
    }
}
