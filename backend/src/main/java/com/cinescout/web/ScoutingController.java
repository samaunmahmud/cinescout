package com.cinescout.web;

import com.cinescout.dto.SceneResponse;
import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.scouting.SceneScoutingService;
import com.cinescout.scouting.ScoutingResult;
import com.cinescout.search.LocationSearchRequest;
import com.cinescout.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequestMapping("/api/scenes/{sceneId}")
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
    @PostMapping("/parse")
    Mono<SceneResponse> parse(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId) {
        return service(RateLimit.AI, user).flatMap(s -> s.parseScene(user.id(), sceneId));
    }

    /**
     * Finds real venues for the scene in its project's location area, assesses each against the scene's
     * requirements and saves the new ones as suggested locations. A scene that has not been parsed is
     * parsed first. Running it again only adds venues not already saved, so shortlists and notes survive.
     *
     * @param maxResults how many venues to look for
     */
    @Operation(summary = "Scout venues for a scene",
            description = "Searches the project's location area, assesses each venue against the scene and saves the new ones as suggested locations.")
    @ApiResponse(responseCode = "409", description = "The scene's project has no location area yet")
    @ApiResponse(responseCode = "429", description = "The user's hourly allowance of scouting runs is used up; see Retry-After")
    @ApiResponse(responseCode = "502", description = "The model returned an unusable answer or a provider key is misconfigured")
    @ApiResponse(responseCode = "503", description = "A provider is unavailable, or scouting is not configured on this server; see Retry-After")
    @PostMapping("/scout")
    Mono<ScoutingResult> scout(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                               @RequestParam(defaultValue = "" + LocationSearchRequest.DEFAULT_MAX_RESULTS)
                               @Min(1) @Max(LocationSearchRequest.MAX_RESULTS) int maxResults) {
        return service(RateLimit.SCOUTING, user).flatMap(s -> s.scout(user.id(), sceneId, maxResults));
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
