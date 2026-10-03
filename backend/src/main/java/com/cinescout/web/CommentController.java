package com.cinescout.web;

import com.cinescout.dto.CommentRequest;
import com.cinescout.dto.CommentResponse;
import com.cinescout.dto.EditCommentRequest;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Tag(name = "Comments", description = "The crew's threaded conversation about a venue, apart from its private notes.")
class CommentController {

    private final CommentService comments;

    CommentController(CommentService comments) {
        this.comments = comments;
    }

    @Operation(summary = "List a venue's comments",
            description = "Threads oldest first, each with all its replies. Comments left through a director link are marked `guest`.")
    @GetMapping("/api/locations/{locationId}/comments")
    Mono<PageResponse<CommentResponse>> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                             @Valid @ParameterObject PageQuery page) {
        return comments.threads(user.id(), locationId, page);
    }

    @Operation(summary = "Comment on a venue, or reply",
            description = "Editors and the owner. `parentId` makes it a reply (one level deep); `mentions` are crew members' ids.")
    @PostMapping("/api/locations/{locationId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    Mono<CommentResponse> post(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                               @Valid @RequestBody CommentRequest request) {
        return comments.post(user.id(), locationId, request);
    }

    @Operation(summary = "Edit a comment", description = "Its author only.")
    @PutMapping("/api/comments/{commentId}")
    Mono<CommentResponse> edit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID commentId,
                               @Valid @RequestBody EditCommentRequest request) {
        return comments.edit(user.id(), commentId, request);
    }

    @Operation(summary = "Delete a comment", description = "Its author, or the project's owner (any comment). Its replies go with it.")
    @DeleteMapping("/api/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID commentId) {
        return comments.delete(user.id(), commentId);
    }
}
