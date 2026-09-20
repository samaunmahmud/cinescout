package com.cinescout.web;

import com.cinescout.dto.CreateLocationRequest;
import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.UpdateLocationRequest;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.LocationService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
class LocationController {

    private final LocationService locations;

    LocationController(LocationService locations) {
        this.locations = locations;
    }

    /** The scene's candidate locations, best fit first; venues added by hand (no score) come last. */
    @GetMapping("/scenes/{sceneId}/locations")
    Mono<List<LocationResponse>> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId) {
        return locations.list(user.id(), sceneId);
    }

    /** Adds a venue the user found themselves. Venues found by scouting are saved by the scout endpoint. */
    @PostMapping("/scenes/{sceneId}/locations")
    Mono<ResponseEntity<LocationResponse>> create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                                                  @Valid @RequestBody CreateLocationRequest request) {
        return locations.create(user.id(), sceneId, request)
                .map(location -> ResponseEntity.created(URI.create("/api/locations/" + location.id())).body(location));
    }

    @GetMapping("/locations/{locationId}")
    Mono<LocationResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId) {
        return locations.get(user.id(), locationId);
    }

    /** Full replacement of the user's own workflow fields (status, notes); the assessment is not editable. */
    @PutMapping("/locations/{locationId}")
    Mono<LocationResponse> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                  @Valid @RequestBody UpdateLocationRequest request) {
        return locations.update(user.id(), locationId, request);
    }

    @DeleteMapping("/locations/{locationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId) {
        return locations.delete(user.id(), locationId);
    }
}
