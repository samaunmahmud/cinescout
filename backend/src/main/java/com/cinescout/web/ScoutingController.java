package com.cinescout.web;

import com.cinescout.dto.SceneResponse;
import com.cinescout.scouting.SceneScoutingService;
import com.cinescout.scouting.ScoutingResult;
import com.cinescout.search.LocationSearchRequest;
import com.cinescout.security.AuthenticatedUser;
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
 * when both the watsonx.ai and Parallel API keys are configured; without them these answer 503.
 */
@RestController
@RequestMapping("/api/scenes/{sceneId}")
class ScoutingController {

    private final ObjectProvider<SceneScoutingService> scouting;

    ScoutingController(ObjectProvider<SceneScoutingService> scouting) {
        this.scouting = scouting;
    }

    /** Extracts the scene's physical filming requirements from its script, replacing any earlier ones. */
    @PostMapping("/parse")
    Mono<SceneResponse> parse(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId) {
        return service().flatMap(s -> s.parseScene(user.id(), sceneId));
    }

    /**
     * Finds real venues for the scene in its project's location area, assesses each against the scene's
     * requirements and saves the new ones as suggested locations. A scene that has not been parsed is
     * parsed first. Running it again only adds venues not already saved, so shortlists and notes survive.
     *
     * @param maxResults how many venues to look for
     */
    @PostMapping("/scout")
    Mono<ScoutingResult> scout(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                               @RequestParam(defaultValue = "" + LocationSearchRequest.DEFAULT_MAX_RESULTS)
                               @Min(1) @Max(LocationSearchRequest.MAX_RESULTS) int maxResults) {
        return service().flatMap(s -> s.scout(user.id(), sceneId, maxResults));
    }

    private Mono<SceneScoutingService> service() {
        return Mono.defer(() -> {
            SceneScoutingService service = scouting.getIfAvailable();
            return service == null
                    ? Mono.error(new FeatureUnavailableException("Scouting is not configured on this server"))
                    : Mono.just(service);
        });
    }
}
