package com.cinescout.dto;

/**
 * What adding someone did: an account that exists joins at once ({@code member}); otherwise an invite is made, whose
 * link the owner passes on ({@code invite}, with its token). Exactly one is set.
 */
public record AddMemberResponse(MemberResponse member, InviteResponse invite) {
}
