package com.cinescout.web;

import com.cinescout.dto.PlanBRequest;
import com.cinescout.dto.PlanBResponse;
import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.PlanBService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Tag(name = "Availability", description = "Holds and bookings of a venue, day by day.")
class PlanBController {

    private final PlanBService planB;
    private final RateLimiter limits;

    PlanBController(PlanBService planB, RateLimiter limits) {
        this.planB = planB;
        this.limits = limits;
    }

    @Operation(summary = "Ask a backup venue to stand by for a day the weather threatens",
            description = "Pencils the venue for the day and has the AI draft an email asking its owner to hold it as a weather backup "
                    + "(one AI call). The pencil is made even when the email cannot be: `draftProblem` then says why.")
    @ApiResponse(responseCode = "409", description = "The venue is the scene's confirmed venue, or was passed on")
    @PostMapping("/api/locations/{locationId}/plan-b")
    Mono<PlanBResponse> ask(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                            @Valid @RequestBody PlanBRequest request) {
        return planB.ask(user.id(), locationId, request, () -> limits.acquire(RateLimit.AI, user.id()));
    }
}
