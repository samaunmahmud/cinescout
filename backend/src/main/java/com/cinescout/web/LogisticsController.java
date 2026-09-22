package com.cinescout.web;

import com.cinescout.logistics.LogisticsReport;
import com.cinescout.logistics.LogisticsService;
import com.cinescout.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * A location's shoot logistics: the light, the weather and the surroundings on the scene's shoot days. They
 * come from free public services (no keys), so this is always available; working them out takes a few seconds
 * and the result is cached on the location.
 */
@RestController
@RequestMapping("/api/locations/{locationId}/logistics")
@Tag(name = "Logistics", description = "Golden and blue hours, weather, noise risk and nearby services for a location's shoot days.")
class LogisticsController {

    private final LogisticsService logistics;

    LogisticsController(LogisticsService logistics) {
        this.logistics = logistics;
    }

    /** The report as it was last worked out; 404 until it has been. */
    @Operation(summary = "Get a location's cached logistics", description = "404 if they have not been worked out yet (POST).")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = LogisticsReport.class)))
    @GetMapping
    Mono<JsonNode> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId) {
        return logistics.cached(user.id(), locationId);
    }

    /**
     * Works the logistics out afresh and caches them. A venue without coordinates is looked up on the map first,
     * and the coordinates found are kept. If the weather or map service is down, the report still comes back,
     * with that section marked UNAVAILABLE.
     */
    @Operation(summary = "Work out a location's logistics",
            description = "Solar times for each shoot day, the weather (a forecast up to about two weeks ahead, the same "
                    + "dates in an earlier year beyond that), the noise risk and nearby services. Takes a few seconds. "
                    + "Sections whose provider is down are marked UNAVAILABLE rather than failing the call; when the map "
                    + "service is busy the call can take up to half a minute.")
    @ApiResponse(responseCode = "409", description = "The venue has no coordinates and could not be found on the map; set them first")
    @ApiResponse(responseCode = "503", description = "The venue needed geocoding and the map service is unavailable; see Retry-After")
    @PostMapping
    Mono<LogisticsReport> refresh(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId) {
        return logistics.refresh(user.id(), locationId);
    }
}
