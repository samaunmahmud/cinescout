package com.cinescout.dto;

import com.cinescout.domain.ProjectRole;

import java.time.Instant;
import java.util.UUID;

/**
 * What an invite link offers, shown before it is accepted.
 *
 * @param state OPEN, ACCEPTED or EXPIRED
 */
public record InvitePreviewResponse(UUID projectId, String projectTitle, String invitedBy, String email, ProjectRole role,
                                    Instant expiresAt, String state) {
}
