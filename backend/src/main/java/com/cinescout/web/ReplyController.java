package com.cinescout.web;

import com.cinescout.dto.ManualReplyRequest;
import com.cinescout.dto.OutreachReplyResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.ReplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Tag(name = "Outreach", description = "AI-drafted emails to venue owners, and the replies to them.")
class ReplyController {

    private final ReplyService replies;

    ReplyController(ReplyService replies) {
        this.replies = replies;
    }

    @Operation(summary = "List the replies to an outreach email", description = "Latest first: by mail to its reply address, or pasted in.")
    @GetMapping("/api/outreach-drafts/{draftId}/replies")
    Mono<PageResponse<OutreachReplyResponse>> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID draftId,
                                                   @Valid @ParameterObject PageQuery page) {
        return replies.list(user.id(), draftId, page);
    }

    @Operation(summary = "Record a reply by hand",
            description = "Paste a reply in, or send nothing to just mark the email replied. Editors and the owner.")
    @PostMapping("/api/outreach-drafts/{draftId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    Mono<OutreachReplyResponse> record(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID draftId,
                                       @Valid @RequestBody ManualReplyRequest request) {
        return replies.record(user.id(), draftId, request);
    }
}
