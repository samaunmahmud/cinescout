package com.cinescout.web;

import com.cinescout.dto.CallSheetLinkResponse;
import com.cinescout.dto.PublicCallSheetResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.CallSheetShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Tag(name = "Call sheet sharing", description = "A link to a project's call sheet for the crew, which works without an account.")
class CallSheetShareController {

    private final CallSheetShareService sharing;

    CallSheetShareController(CallSheetShareService sharing) {
        this.sharing = sharing;
    }

    @Operation(summary = "Get a project's call sheet link", description = "404 while the call sheet is not shared.")
    @GetMapping("/api/projects/{projectId}/call-sheet-link")
    Mono<CallSheetLinkResponse> link(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return sharing.link(user.id(), projectId);
    }

    @Operation(summary = "Share a project's call sheet",
            description = "Makes a new link (replacing any earlier one, which stops working). Anyone with it can read the call sheet: "
                    + "the schedule, the confirmed venues and their contacts.")
    @PostMapping("/api/projects/{projectId}/call-sheet-link")
    Mono<CallSheetLinkResponse> share(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return sharing.share(user.id(), projectId);
    }

    @Operation(summary = "Stop sharing a project's call sheet", description = "The link stops working at once.")
    @DeleteMapping("/api/projects/{projectId}/call-sheet-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> stopSharing(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return sharing.stopSharing(user.id(), projectId);
    }

    /** No account needed: the token is the permission. Kept out of caches and search engines. */
    @Operation(summary = "Read a shared call sheet", description = "Public: the token from a call sheet link is all it takes. 404 once sharing has stopped.")
    @SecurityRequirements
    @ApiResponse(responseCode = "404", description = "The link is not, or no longer, shared")
    @GetMapping("/api/public/call-sheets/{token}")
    Mono<ResponseEntity<PublicCallSheetResponse>> sheet(@PathVariable String token) {
        return sharing.sheet(token).map(sheet -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(sheet));
    }
}
