package com.cinescout.web;

import com.cinescout.dto.BookingRouteRequest;
import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.PermitGuidance;
import com.cinescout.permits.PermitService;
import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Tag(name = "Locations", description = "Candidate venues for a scene: found by scouting or added by hand.")
class PermitController {

    private final PermitService permits;
    private final LocationService locations;
    private final RateLimiter limits;

    PermitController(PermitService permits, LocationService locations, RateLimiter limits) {
        this.permits = permits;
        this.locations = locations;
        this.limits = limits;
    }

    @Operation(summary = "Get the permit guide for a venue",
            description = "The filming office for the local authority area of the venue's position (London boroughs so far; the local "
                    + "council elsewhere in the UK), how many working days ahead to apply for the scene's crew size, and what to have "
                    + "ready. The area is looked up once (OpenStreetMap Nominatim, counted as a lookup) and kept until the venue moves.")
    @GetMapping("/api/locations/{locationId}/permit")
    Mono<PermitGuidance> permit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId) {
        return permits.guidance(user.id(), locationId, () -> limits.acquire(RateLimit.LOOKUPS, user.id()));
    }

    @Operation(summary = "Set who has to say yes to filming at a venue",
            description = "PUBLIC (a public space), COMMERCIAL or PRIVATE, or null to clear it. Replaces what scouting assessed.")
    @PutMapping("/api/locations/{locationId}/booking-route")
    Mono<LocationResponse> setBookingRoute(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                           @Valid @RequestBody BookingRouteRequest request) {
        return locations.setBookingRoute(user.id(), locationId, request.bookingFriction());
    }
}
