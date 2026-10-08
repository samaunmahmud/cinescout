package com.cinescout.web;

import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.SceneResponse;
import com.cinescout.dto.ScoutRequest;
import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.scouting.BatchParseResult;
import com.cinescout.scouting.SceneScoutingService;
import com.cinescout.scouting.ScoutingResult;
import com.cinescout.search.LocationSearchRequest;
import com.cinescout.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * The AI-backed endpoints. They call paid external services and take seconds (longer when a provider
 * is struggling and retries kick in), so clients should set generous timeouts. The service only exists
 * when both the watsonx.ai and Parallel API keys are configured; without them these answer 503. Each user gets a
 * limited number of calls an hour (429 beyond it).
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Scouting", description = "AI-backed endpoints. They call paid external services and can take many seconds.")
class ScoutingController {

    private final ObjectProvider<SceneScoutingService> scouting;
    private final RateLimiter limits;

    ScoutingController(ObjectProvider<SceneScoutingService> scouting, RateLimiter limits) {
        this.scouting = scouting;
        this.limits = limits;
    }

    /** Extracts the scene's physical filming requirements from its script, replacing any earlier ones. */
    @Operation(summary = "Extract a scene's filming requirements")
    @ApiResponse(responseCode = "429", description = "The user's hourly allowance of AI calls is used up; see Retry-After")
    @ApiResponse(responseCode = "502", description = "The model returned an unusable answer (the scene is marked FAILED) or a provider key is misconfigured")
    @ApiResponse(responseCode = "503", description = "A provider is unavailable, or scouting is not configured on this server; see Retry-After")
    @PostMapping("/scenes/{sceneId}/parse")
    Mono<SceneResponse> parse(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId) {
        return service(RateLimit.AI, user).flatMap(s -> s.parseScene(user.id(), sceneId));
    }

    /**
     * Analyses the project's scenes that are still waiting for it, e.g. after a script import. Each scene counts
     * against the user's hourly allowance of AI calls, as it would parsed on its own.
     */
    @Operation(summary = "Extract the requirements of a project's unanalysed scenes",
            description = "Analyses up to " + SceneScoutingService.MAX_BATCH + " scenes that have not been analysed yet, in script order, and says how many "
                    + "are left: run it again to go on. A scene the model cannot make sense of is marked FAILED and the run continues. "
                    + "Each scene counts as one AI call against the user's allowance; when that or the AI service gives out part-way, "
                    + "the answer reports what was done and the rest stays pending.")
    @ApiResponse(responseCode = "429", description = "The user's hourly allowance of AI calls is used up and no scene was analysed; see Retry-After")
    @ApiResponse(responseCode = "502", description = "A provider key is misconfigured")
    @ApiResponse(responseCode = "503", description = "The AI service is unavailable and no scene was analysed, or scouting is not configured on this server; see Retry-After")
    @PostMapping("/projects/{projectId}/scenes/parse")
    Mono<BatchParseResult> parseProject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return Mono.defer(() -> {
            SceneScoutingService service = scouting.getIfAvailable();
            return service == null
                    ? Mono.error(new FeatureUnavailableException("Scouting is not configured on this server"))
                    : service.parseProject(user.id(), projectId, () -> limits.acquire(RateLimit.AI, user.id()));
        });
    }

    /**
     * Finds real venues for the scene in its project's location area, assesses each against the scene's
     * requirements and saves the new ones as suggested locations. A scene that has not been parsed is
     * parsed first. Running it again only adds venues not already saved, so shortlists and notes survive.
     *
     * @param maxResults how many venues to look for
     */
    @Operation(summary = "Scout venues for a scene",
            description = "Searches the project's location area, assesses each venue against the scene and saves the new ones as suggested locations. "
                    + "Keeps to the project's scouting filters, or to `filters` in the body for this run; `filteredOut` counts what they left out.")
    @ApiResponse(responseCode = "409", description = "The scene's project has no location area yet")
    @ApiResponse(responseCode = "429", description = "The user's hourly allowance of scouting runs is used up; see Retry-After")
    @ApiResponse(responseCode = "502", description = "The model returned an unusable answer or a provider key is misconfigured")
    @ApiResponse(responseCode = "503", description = "A provider is unavailable, or scouting is not configured on this server; see Retry-After")
    @PostMapping("/scenes/{sceneId}/scout")
    Mono<ScoutingResult> scout(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                               @RequestParam(defaultValue = "" + LocationSearchRequest.DEFAULT_MAX_RESULTS)
                               @Min(1) @Max(LocationSearchRequest.MAX_RESULTS) int maxResults,
                               @Valid @RequestBody(required = false) ScoutRequest request) {
        return service(RateLimit.SCOUTING, user).flatMap(s -> s.scout(user.id(), sceneId, maxResults, request == null ? null : request.filters()));
    }

    /**
     * Has the AI assess a venue already on a scene, e.g. one added by hand or from the library, as scouting assesses
     * what it finds. Counts as one AI call (two when the scene has not been analysed yet and is analysed first).
     */
    @Operation(summary = "Assess a venue with AI",
            description = "Scores the venue against its scene's requirements from what is known of it (name, address, notes, "
                    + "its web page's excerpt) and stores the fit score, booking route and warnings. Name, pin, status and notes "
                    + "are kept. A scene not analysed yet is analysed first.")
    @ApiResponse(responseCode = "429", description = "The user's hourly allowance of AI calls is used up; see Retry-After")
    @ApiResponse(responseCode = "502", description = "The model returned an unusable answer or a provider key is misconfigured")
    @ApiResponse(responseCode = "503", description = "The AI service is unavailable, or scouting is not configured on this server; see Retry-After")
    @PostMapping("/locations/{locationId}/assess")
    Mono<LocationResponse> assess(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId) {
        return service(RateLimit.AI, user).flatMap(s -> s.assessVenue(user.id(), locationId));
    }

    /** The service, once the call is within the user's limit; an unconfigured server does not count the call. */
    private Mono<SceneScoutingService> service(RateLimit limit, AuthenticatedUser user) {
        return Mono.defer(() -> {
            SceneScoutingService service = scouting.getIfAvailable();
            return service == null
                    ? Mono.error(new FeatureUnavailableException("Scouting is not configured on this server"))
                    : limits.acquire(limit, user.id()).thenReturn(service);
        });
    }
}
