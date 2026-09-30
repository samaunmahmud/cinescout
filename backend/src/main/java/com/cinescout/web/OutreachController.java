package com.cinescout.web;

import com.cinescout.domain.OutreachStatus;
import com.cinescout.dto.GenerateOutreachRequest;
import com.cinescout.dto.OutreachDraftResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.ProjectOutreachResponse;
import com.cinescout.dto.UpdateOutreachRequest;
import com.cinescout.outreach.OutreachGenerationService;
import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.OutreachService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

/**
 * Outreach emails to venue owners. Generating a draft calls the LLM (paid, takes seconds) and only exists
 * when the watsonx.ai key is configured; reading, editing and deleting drafts always works.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Outreach", description = "Emails to venue owners. Drafts are written by the model for the user to edit; nothing is ever sent by the API.")
class OutreachController {

    private final OutreachService outreach;
    private final ObjectProvider<OutreachGenerationService> generation;
    private final RateLimiter limits;

    OutreachController(OutreachService outreach, ObjectProvider<OutreachGenerationService> generation, RateLimiter limits) {
        this.outreach = outreach;
        this.generation = generation;
        this.limits = limits;
    }

    /** Who has been written to across the project, and how far each email got. */
    @Operation(summary = "List a project's outreach drafts across all its locations, newest first",
            description = "Each row names its venue and scene; `status` narrows the list (DRAFT, SENT or REPLIED). The email text is on the draft itself.")
    @GetMapping("/projects/{projectId}/outreach-drafts")
    Mono<PageResponse<ProjectOutreachResponse>> listForProject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                                               @RequestParam(required = false) OutreachStatus status,
                                                               @Valid @ParameterObject PageQuery page) {
        return outreach.listForProject(user.id(), projectId, status, page);
    }

    /** A location's drafts, newest first. */
    @Operation(summary = "List a location's outreach drafts, newest first")
    @GetMapping("/locations/{locationId}/outreach-drafts")
    Mono<PageResponse<OutreachDraftResponse>> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                                   @Valid @ParameterObject PageQuery page) {
        return outreach.list(user.id(), locationId, page);
    }

    /**
     * Has the model write a new email to the venue's owner and saves it as a draft. The body is optional:
     * without it the tone is professional and nothing is pre-filled. Each call writes a new draft, so tone
     * variations and follow-ups accumulate on the location.
     */
    @Operation(summary = "Generate an outreach email for a location",
            description = "The model sees the venue, the scene's requirements (never the script), the sender's name and the shoot dates. Calls a paid service and can take many seconds.")
    @ApiResponse(responseCode = "201", description = "Created; the Location header points at the new draft")
    @ApiResponse(responseCode = "429", description = "The user's hourly allowance of AI calls is used up; see Retry-After")
    @ApiResponse(responseCode = "502", description = "The model returned an unusable answer or the provider key is misconfigured")
    @ApiResponse(responseCode = "503", description = "The AI provider is unavailable, or generation is not configured on this server; see Retry-After")
    @PostMapping("/locations/{locationId}/outreach-drafts/generate")
    Mono<ResponseEntity<OutreachDraftResponse>> generate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                                         @Valid @RequestBody(required = false) GenerateOutreachRequest request) {
        return service(user).flatMap(s -> s.generate(user.id(), locationId, request))
                .map(draft -> ResponseEntity.created(URI.create("/api/outreach-drafts/" + draft.id())).body(draft));
    }

    @Operation(summary = "Get an outreach draft")
    @GetMapping("/outreach-drafts/{draftId}")
    Mono<OutreachDraftResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID draftId) {
        return outreach.get(user.id(), draftId);
    }

    /** Full replacement of the editable fields; a null recipient name or email clears it. */
    @Operation(summary = "Edit an outreach draft",
            description = "Full replacement. Setting the status to SENT or REPLIED records when (sentAt); setting it back to DRAFT clears it. The API never sends the email; the status is what the user reports.")
    @PutMapping("/outreach-drafts/{draftId}")
    Mono<OutreachDraftResponse> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID draftId,
                                       @Valid @RequestBody UpdateOutreachRequest request) {
        return outreach.update(user.id(), draftId, request);
    }

    @Operation(summary = "Delete an outreach draft")
    @DeleteMapping("/outreach-drafts/{draftId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID draftId) {
        return outreach.delete(user.id(), draftId);
    }

    /** The service, once the call is within the user's AI limit; an unconfigured server does not count the call. */
    private Mono<OutreachGenerationService> service(AuthenticatedUser user) {
        return Mono.defer(() -> {
            OutreachGenerationService service = generation.getIfAvailable();
            return service == null
                    ? Mono.error(new FeatureUnavailableException("Outreach generation is not configured on this server"))
                    : limits.acquire(RateLimit.AI, user.id()).thenReturn(service);
        });
    }
}
