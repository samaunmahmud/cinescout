package com.cinescout.dto;

import java.util.List;

/**
 * A project's crew: every member (a project has a handful, so the list is not paged), and for its owner the
 * invites still open. Other members see no invites.
 */
public record CrewResponse(List<MemberResponse> members, List<InviteResponse> invites) {
}
