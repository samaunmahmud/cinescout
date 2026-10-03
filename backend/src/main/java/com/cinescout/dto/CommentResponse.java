package com.cinescout.dto;

import com.cinescout.domain.User;
import com.cinescout.domain.VenueComment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A comment on a venue, with its replies when it starts a thread. {@code guest} marks a comment left through a
 * director link, under {@code authorName} as the guest typed it; {@code authorId} is null for those and for a
 * member whose account is gone (then {@code authorName} is null too). {@code edited} once changed after posting.
 */
public record CommentResponse(
        UUID id,
        UUID locationId,
        UUID parentId,
        UUID authorId,
        String authorName,
        boolean guest,
        String body,
        List<Mention> mentions,
        boolean edited,
        Instant createdAt,
        Instant updatedAt,
        List<CommentResponse> replies
) {

    /** A member a comment @mentions, by the name it shows. */
    public record Mention(UUID userId, String displayName) {
    }

    /** Edits within this long of posting do not count. */
    private static final Duration GRACE = Duration.ofSeconds(5);

    public static CommentResponse from(VenueComment comment, Map<UUID, User> mentioned, List<CommentResponse> replies) {
        User author = comment.getAuthor();
        List<Mention> mentions = comment.getMentions().stream()
                .map(mentioned::get)
                .filter(Objects::nonNull)
                .map(user -> new Mention(user.getId(), user.getDisplayName()))
                .toList();
        boolean edited = comment.getCreatedAt() != null && comment.getUpdatedAt() != null
                && comment.getUpdatedAt().isAfter(comment.getCreatedAt().plus(GRACE));
        return new CommentResponse(comment.getId(), comment.getLocation().getId(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                author == null ? null : author.getId(),
                comment.isByGuest() ? comment.getGuestName() : author == null ? null : author.getDisplayName(),
                comment.isByGuest(), comment.getBody(), mentions, edited, comment.getCreatedAt(), comment.getUpdatedAt(), replies);
    }
}
