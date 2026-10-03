package com.cinescout.web;

import com.cinescout.dto.AddMemberRequest;
import com.cinescout.dto.AddMemberResponse;
import com.cinescout.dto.ChangeRoleRequest;
import com.cinescout.dto.CrewResponse;
import com.cinescout.dto.InvitePreviewResponse;
import com.cinescout.dto.MemberResponse;
import com.cinescout.dto.TransferOwnershipRequest;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.CrewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@Tag(name = "Crew", description = "Who is on a project and what each may do: OWNER, EDITOR or VIEWER.")
class CrewController {

    private final CrewService crew;

    CrewController(CrewService crew) {
        this.crew = crew;
    }

    @Operation(summary = "List a project's crew", description = "Every member; the owner also sees invites not yet taken up.")
    @GetMapping("/api/projects/{projectId}/members")
    Mono<CrewResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return crew.crew(user.id(), projectId);
    }

    @Operation(summary = "Add someone to the crew",
            description = "Owner only. An existing account joins at once; otherwise an invite is made and its link's token returned "
                    + "once (CineScout sends no email: pass the link on). Invites last 7 days and work once.")
    @PostMapping("/api/projects/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    Mono<AddMemberResponse> add(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                @Valid @RequestBody AddMemberRequest request) {
        return crew.add(user.id(), projectId, request);
    }

    @Operation(summary = "Change a member's role", description = "Owner only; EDITOR or VIEWER. The owner's role changes by a transfer.")
    @PutMapping("/api/projects/{projectId}/members/{memberId}")
    Mono<MemberResponse> changeRole(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                    @PathVariable UUID memberId, @Valid @RequestBody ChangeRoleRequest request) {
        return crew.changeRole(user.id(), projectId, memberId, request.role());
    }

    @Operation(summary = "Remove a member, or leave",
            description = "The owner removes anyone else; any member can remove themselves. The owner must hand ownership over first.")
    @DeleteMapping("/api/projects/{projectId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId, @PathVariable UUID memberId) {
        return crew.remove(user.id(), projectId, memberId);
    }

    @Operation(summary = "Hand ownership to another member", description = "Owner only. The current owner stays on as an EDITOR.")
    @PostMapping("/api/projects/{projectId}/transfer")
    Mono<CrewResponse> transfer(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                @Valid @RequestBody TransferOwnershipRequest request) {
        return crew.transfer(user.id(), projectId, request.userId());
    }

    @Operation(summary = "Withdraw an invite", description = "Owner only. The link stops working.")
    @DeleteMapping("/api/projects/{projectId}/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> revokeInvite(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId, @PathVariable UUID inviteId) {
        return crew.revokeInvite(user.id(), projectId, inviteId);
    }

    @Operation(summary = "See what an invite offers", description = "For any logged-in account; 404 for a link that is not valid.")
    @GetMapping("/api/invites/{token}")
    Mono<ResponseEntity<InvitePreviewResponse>> preview(@PathVariable String token) {
        return crew.preview(token).map(invite -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invite));
    }

    @Operation(summary = "Accept an invite",
            description = "Joins the project. The account's email must be the one invited (403 otherwise); 409 once used or expired.")
    @PostMapping("/api/invites/{token}/accept")
    Mono<InvitePreviewResponse> accept(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String token) {
        return crew.accept(user.id(), token);
    }
}
