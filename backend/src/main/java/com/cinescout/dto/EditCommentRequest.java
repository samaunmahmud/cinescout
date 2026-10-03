package com.cinescout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** A comment's new wording, with the members it now @mentions. */
public record EditCommentRequest(
        @NotBlank @Size(max = 4000) String body,
        @Size(max = 20) List<UUID> mentions
) {

    public List<UUID> mentionIds() {
        return mentions == null ? List.of() : mentions;
    }
}
