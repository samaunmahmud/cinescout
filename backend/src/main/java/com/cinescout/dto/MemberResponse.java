package com.cinescout.dto;

import com.cinescout.domain.ProjectMember;
import com.cinescout.domain.ProjectRole;

import java.time.Instant;
import java.util.UUID;

/** One of a project's crew. */
public record MemberResponse(UUID userId, String displayName, String email, ProjectRole role, Instant joinedAt) {

    public static MemberResponse from(ProjectMember member) {
        return new MemberResponse(member.getUser().getId(), member.getUser().getDisplayName(), member.getUser().getEmail(),
                member.getRole(), member.getJoinedAt());
    }
}
