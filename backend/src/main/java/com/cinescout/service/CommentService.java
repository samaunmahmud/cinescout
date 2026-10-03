package com.cinescout.service;

import com.cinescout.domain.Location;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.User;
import com.cinescout.domain.VenueComment;
import com.cinescout.dto.CommentRequest;
import com.cinescout.dto.CommentResponse;
import com.cinescout.dto.EditCommentRequest;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.ProjectMemberRepository;
import com.cinescout.repository.UserRepository;
import com.cinescout.repository.VenueCommentRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The crew's comments on a venue, in threads one reply deep. Everyone on the crew reads them; editors and the owner
 * write. Authors edit and delete their own; the owner may delete anyone's, guests' included. A guest's comment comes
 * from their call on a director link and is kept in step with it there (see {@link DirectorLinkService}).
 */
@Service
public class CommentService {

    private final VenueCommentRepository comments;
    private final ProjectMemberRepository members;
    private final UserRepository users;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public CommentService(VenueCommentRepository comments, ProjectMemberRepository members, UserRepository users,
                          ProjectAccess access, BlockingTransactions db) {
        this.comments = comments;
        this.members = members;
        this.users = users;
        this.access = access;
        this.db = db;
    }

    /** A page of the venue's threads, oldest first, each with all its replies. */
    public Mono<PageResponse<CommentResponse>> threads(UUID userId, UUID locationId, PageQuery page) {
        return db.call(() -> {
            access.location(userId, locationId, ProjectRole.VIEWER);
            Page<VenueComment> threads = comments.findThreads(locationId, page.pageable());
            List<VenueComment> replies = threads.isEmpty() ? List.of()
                    : comments.findReplies(threads.map(VenueComment::getId).getContent());
            Map<UUID, User> mentioned = mentioned(Stream.concat(threads.stream(), replies.stream()).toList());
            Map<UUID, List<CommentResponse>> repliesByThread = replies.stream().collect(Collectors.groupingBy(
                    reply -> reply.getParent().getId(),
                    Collectors.mapping(reply -> CommentResponse.from(reply, mentioned, List.of()), Collectors.toList())));
            return PageResponse.from(threads, thread -> CommentResponse.from(thread, mentioned,
                    repliesByThread.getOrDefault(thread.getId(), List.of())));
        });
    }

    public Mono<CommentResponse> post(UUID userId, UUID locationId, CommentRequest request) {
        return db.call(() -> {
            Location location = access.location(userId, locationId, ProjectRole.EDITOR);
            VenueComment parent = null;
            if (request.parentId() != null) {
                VenueComment replyingTo = comments.findById(request.parentId())
                        .filter(comment -> comment.getLocation().getId().equals(locationId))
                        .orElseThrow(() -> new InvalidRequestException("parentId", "That comment is not on this venue"));
                // One level of replies: answering a reply joins the thread it is in.
                parent = replyingTo.getParent() != null ? replyingTo.getParent() : replyingTo;
            }
            List<UUID> mentions = crewOnly(location.getScene().getProject().getId(), request.mentionIds());
            VenueComment saved = comments.saveAndFlush(VenueComment.byMember(location, parent, users.getReferenceById(userId),
                    request.body().strip(), mentions));
            return CommentResponse.from(saved, mentioned(List.of(saved)), List.of());
        });
    }

    /** The author rewords their comment; they must still be able to write on the project. */
    public Mono<CommentResponse> edit(UUID userId, UUID commentId, EditCommentRequest request) {
        return db.call(() -> {
            VenueComment comment = visible(userId, commentId);
            UUID projectId = comment.getLocation().getScene().getProject().getId();
            access.project(userId, projectId, ProjectRole.EDITOR);
            if (!isAuthor(userId, comment)) {
                throw new ForbiddenException("Only the person who wrote a comment can edit it");
            }
            comment.edit(request.body().strip(), crewOnly(projectId, request.mentionIds()));
            VenueComment saved = comments.saveAndFlush(comment);
            return CommentResponse.from(saved, mentioned(List.of(saved)), List.of());
        });
    }

    /** By its author, or by the owner (any comment, a guest's too). Its replies go with it. */
    public Mono<Void> delete(UUID userId, UUID commentId) {
        return db.run(() -> {
            VenueComment comment = visible(userId, commentId);
            UUID projectId = comment.getLocation().getScene().getProject().getId();
            if (!isAuthor(userId, comment) && access.role(userId, projectId) != ProjectRole.OWNER) {
                throw new ForbiddenException("Only the person who wrote a comment, or the project's owner, can delete it");
            }
            comments.delete(comment);
        });
    }

    /** The comment, if its venue is on a project the user is on; otherwise not found, as if it did not exist. */
    private VenueComment visible(UUID userId, UUID commentId) {
        VenueComment comment = comments.findWithProject(commentId).orElseThrow(() -> new NotFoundException("Comment", commentId));
        UUID projectId = comment.getLocation().getScene().getProject().getId();
        if (members.findRole(projectId, userId).isEmpty()) {
            throw new NotFoundException("Comment", commentId);
        }
        return comment;
    }

    private static boolean isAuthor(UUID userId, VenueComment comment) {
        return comment.getAuthor() != null && comment.getAuthor().getId().equals(userId);
    }

    /** The mentioned ids that belong to the project's crew, each once, in the order given. */
    private List<UUID> crewOnly(UUID projectId, List<UUID> mentions) {
        Set<UUID> unique = new LinkedHashSet<>(mentions);
        return unique.stream().filter(id -> id != null && members.findRole(projectId, id).isPresent()).toList();
    }

    private Map<UUID, User> mentioned(Collection<VenueComment> all) {
        Set<UUID> ids = all.stream().flatMap(comment -> comment.getMentions().stream()).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
