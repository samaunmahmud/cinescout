package com.cinescout.web;

import com.cinescout.dto.DirectorLinkRequest;
import com.cinescout.dto.DirectorLinkResponse;
import com.cinescout.dto.DirectorResponseRequest;
import com.cinescout.dto.DirectorResponseResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.PublicShortlistResponse;
import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.security.ClientAddress;
import com.cinescout.service.DirectorLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Tag(name = "Director links", description = "A link without a login that shows the shortlisted venues to a director, who answers each.")
class DirectorLinkController {

    private static final String WHAT_IT_SHOWS = "Anyone with it sees the shortlisted, contacted and confirmed venues: picture, fit score, "
            + "booking note, warnings and map position. Private notes, quotes and the reason for the fit score only with showPrivate "
            + "(owner only). Making a new link replaces the old one, which stops working.";

    private final DirectorLinkService links;
    private final RateLimiter limits;

    DirectorLinkController(DirectorLinkService links, RateLimiter limits) {
        this.links = links;
        this.limits = limits;
    }

    @Operation(summary = "Get a project's director link", description = "404 while there is none.")
    @GetMapping("/api/projects/{projectId}/director-link")
    Mono<DirectorLinkResponse> projectLink(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return links.link(user.id(), projectId, null);
    }

    @Operation(summary = "Share a project's shortlist with a director", description = WHAT_IT_SHOWS)
    @PostMapping("/api/projects/{projectId}/director-link")
    Mono<DirectorLinkResponse> shareProject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                            @RequestBody(required = false) DirectorLinkRequest request) {
        return links.share(user.id(), projectId, null, request);
    }

    @Operation(summary = "Withdraw a project's director link", description = "It stops working at once; the calls already given stay.")
    @DeleteMapping("/api/projects/{projectId}/director-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> stopProject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return links.stopSharing(user.id(), projectId, null);
    }

    @Operation(summary = "Get a scene's director link", description = "404 while there is none.")
    @GetMapping("/api/scenes/{sceneId}/director-link")
    Mono<DirectorLinkResponse> sceneLink(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId) {
        return links.link(user.id(), null, sceneId);
    }

    @Operation(summary = "Share one scene's shortlist with a director", description = WHAT_IT_SHOWS)
    @PostMapping("/api/scenes/{sceneId}/director-link")
    Mono<DirectorLinkResponse> shareScene(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                                          @RequestBody(required = false) DirectorLinkRequest request) {
        return links.share(user.id(), null, sceneId, request);
    }

    @Operation(summary = "Withdraw a scene's director link", description = "It stops working at once; the calls already given stay.")
    @DeleteMapping("/api/scenes/{sceneId}/director-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> stopScene(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId) {
        return links.stopSharing(user.id(), null, sceneId);
    }

    @Operation(summary = "List the director's calls on a venue", description = "Latest first; one per guest name.")
    @GetMapping("/api/locations/{locationId}/director-responses")
    Mono<PageResponse<DirectorResponseResponse>> forLocation(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                                             @Valid @ParameterObject PageQuery page) {
        return links.forLocation(user.id(), locationId, page);
    }

    @Operation(summary = "List the director's calls on a scene's venues", description = "Latest first; each names its venue.")
    @GetMapping("/api/scenes/{sceneId}/director-responses")
    Mono<PageResponse<DirectorResponseResponse>> forScene(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                                                          @Valid @ParameterObject PageQuery page) {
        return links.forScene(user.id(), sceneId, page);
    }

    /** No account needed: the token is the permission. Kept out of caches and search engines. */
    @Operation(summary = "Read a shared shortlist", description = "Public: the token from a director link is all it takes. 404 once withdrawn.")
    @SecurityRequirements
    @ApiResponse(responseCode = "404", description = "The link is not, or no longer, shared")
    @GetMapping("/api/public/shortlists/{token}")
    Mono<ResponseEntity<PublicShortlistResponse>> shortlist(@PathVariable String token, @Valid @ParameterObject PageQuery page) {
        return links.shortlist(token, page).map(shortlist -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(shortlist));
    }

    @Operation(summary = "Answer a venue on a shared shortlist",
            description = "Public. Approve, Maybe or No with an optional comment, under the name the guest types; the same name "
                    + "answering again replaces the earlier call. Limited per client address.")
    @SecurityRequirements
    @ApiResponse(responseCode = "404", description = "The link is withdrawn, or does not show that venue")
    @ApiResponse(responseCode = "429", description = "Too many answers from this address")
    @PostMapping("/api/public/shortlists/{token}/venues/{locationId}/response")
    Mono<DirectorResponseResponse> respond(@PathVariable String token, @PathVariable UUID locationId,
                                           @Valid @RequestBody DirectorResponseRequest request, ServerWebExchange exchange) {
        return limits.acquire(RateLimit.GUEST, ClientAddress.of(exchange)).then(Mono.defer(() -> links.respond(token, locationId, request)));
    }
}
