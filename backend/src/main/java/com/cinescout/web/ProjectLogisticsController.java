package com.cinescout.web;

import com.cinescout.logistics.ProjectLogisticsService;
import com.cinescout.logistics.ProjectLogisticsService.BatchLogisticsResult;
import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Tag(name = "Logistics")
class ProjectLogisticsController {

    private final ProjectLogisticsService logistics;
    private final RateLimiter limits;

    ProjectLogisticsController(ProjectLogisticsService logistics, RateLimiter limits) {
        this.logistics = logistics;
        this.limits = limits;
    }

    @Operation(summary = "Work out the logistics of a project's confirmed venues that have none yet",
            description = "Up to " + ProjectLogisticsService.MAX_BATCH + " venues a call, one at a time, each counting as one lookup against the "
                    + "user's allowance; the answer says how many are left. Venues that cannot be worked out are counted as failed and the run goes on.")
    @ApiResponse(responseCode = "429", description = "The user's hourly allowance of lookups is used up and no venue was done; see Retry-After")
    @PostMapping("/api/projects/{projectId}/logistics")
    Mono<BatchLogisticsResult> refreshConfirmed(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return logistics.refreshConfirmed(user.id(), projectId, () -> limits.acquire(RateLimit.LOOKUPS, user.id()));
    }
}
