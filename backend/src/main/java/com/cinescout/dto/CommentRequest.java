package com.cinescout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * A new comment on a venue, or a reply when {@code parentId} is set (a reply to a reply joins its thread).
 * {@code mentions} are the ids of the members it @mentions; ids of people not on the crew are dropped.
 */
public record CommentRequest(
        @NotBlank @Size(max = 4000) String body,
        UUID parentId,
        @Size(max = 20) List<UUID> mentions
) {

    public List<UUID> mentionIds() {
        return mentions == null ? List.of() : mentions;
    }
}
