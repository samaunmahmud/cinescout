package com.cinescout.web;

import com.cinescout.domain.ScoutFilters;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.ScoutFilterService;
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
@Tag(name = "Scouting", description = "AI-backed endpoints. They call paid external services and can take many seconds.")
class ScoutFilterController {

    private final ScoutFilterService filters;

    ScoutFilterController(ScoutFilterService filters) {
        this.filters = filters;
    }

    @Operation(summary = "Get a project's scouting filters", description = "Every field empty while none are set.")
    @GetMapping("/api/projects/{projectId}/scout-filters")
    Mono<ScoutFilters> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return filters.get(user.id(), projectId);
    }

    @Operation(summary = "Set a project's scouting filters",
            description = "A full replacement. Scouting runs keep to them unless a run brings its own: a base point (address, or "
                    + "latitude and longitude) with a radius in km, a maximum day rate, venue types to leave out, and whether "
                    + "private property is wanted.")
    @PutMapping("/api/projects/{projectId}/scout-filters")
    Mono<ScoutFilters> set(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                           @Valid @RequestBody ScoutFilters request) {
        return filters.set(user.id(), projectId, request);
    }
}
