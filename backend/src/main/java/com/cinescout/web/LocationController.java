package com.cinescout.web;

import com.cinescout.domain.LocationStatus;
import com.cinescout.dto.CreateLocationRequest;
import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.ProjectLocationResponse;
import com.cinescout.dto.UpdateContactRequest;
import com.cinescout.dto.UpdateCoordinatesRequest;
import com.cinescout.dto.UpdateLocationRequest;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Locations", description = "Candidate venues for a scene: found by scouting or added by hand.")
class LocationController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final LocationService locations;

    LocationController(LocationService locations) {
        this.locations = locations;
    }

    /** The scene's candidate locations, best fit first; venues added by hand (no score) come last. */
    @Operation(summary = "List a scene's candidate locations, best fit first")
    @GetMapping("/scenes/{sceneId}/locations")
    Mono<PageResponse<LocationResponse>> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                                              @Valid @ParameterObject PageQuery page) {
        return locations.list(user.id(), sceneId, page);
    }

    /** The shortlist view: every scene's candidates in one list, optionally only those in one status. */
    @Operation(summary = "List a project's candidate locations across all its scenes",
            description = "Scene by scene in script order, best fit first within a scene; `status` narrows the list (e.g. SHORTLISTED). "
                    + "Each row names its scene; the page excerpt, warnings and logistics are on the location itself.")
    @GetMapping("/projects/{projectId}/locations")
    Mono<PageResponse<ProjectLocationResponse>> listForProject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                                               @RequestParam(required = false) LocationStatus status,
                                                               @Valid @ParameterObject PageQuery page) {
        return locations.listForProject(user.id(), projectId, status, page);
    }

    /** The project-wide list as a file, for people who do not use the app. */
    @Operation(summary = "Download a project's candidate locations as a spreadsheet",
            description = "The same list as GET /projects/{projectId}/locations, all of it, as CSV (UTF-8): one venue a row with its scene, "
                    + "status, fit, booking friction, address, coordinates, web page, warnings and notes. `status` narrows it.")
    @ApiResponse(responseCode = "200", description = "The CSV file, as an attachment",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string")))
    @GetMapping(value = "/projects/{projectId}/locations/export", produces = "text/csv")
    Mono<ResponseEntity<String>> exportForProject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                                  @RequestParam(required = false) LocationStatus status) {
        return locations.exportForProject(user.id(), projectId, status)
                .map(export -> ResponseEntity.ok()
                        .contentType(CSV)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.filename() + "\"")
                        .body(export.csv()));
    }

    /** Adds a venue the user found themselves. Venues found by scouting are saved by the scout endpoint. */
    @Operation(summary = "Add a venue by hand", description = "The same page can only be saved once per scene (409 otherwise).")
    @ApiResponse(responseCode = "201", description = "Created; the Location header points at the new location")
    @PostMapping("/scenes/{sceneId}/locations")
    Mono<ResponseEntity<LocationResponse>> create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                                                  @Valid @RequestBody CreateLocationRequest request) {
        return locations.create(user.id(), sceneId, request)
                .map(location -> ResponseEntity.created(URI.create("/api/locations/" + location.id())).body(location));
    }

    @Operation(summary = "Get a location")
    @GetMapping("/locations/{locationId}")
    Mono<LocationResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId) {
        return locations.get(user.id(), locationId);
    }

    /** Full replacement of the user's own workflow fields (status, notes); the assessment is not editable. */
    @Operation(summary = "Update a location's status and notes", description = "Only the user's own workflow fields are editable; the assessment is not.")
    @PutMapping("/locations/{locationId}")
    Mono<LocationResponse> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                  @Valid @RequestBody UpdateLocationRequest request) {
        return locations.update(user.id(), locationId, request);
    }

    /** Sets where the venue is. Its cached logistics are dropped, as they were worked out for the old spot. */
    @Operation(summary = "Set a location's coordinates",
            description = "For a venue that could not be found on the map, or whose pin is wrong. Drops its cached logistics.")
    @PutMapping("/locations/{locationId}/coordinates")
    Mono<LocationResponse> relocate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                    @Valid @RequestBody UpdateCoordinatesRequest request) {
        return locations.relocate(user.id(), locationId, request);
    }

    /** Records who to talk to at the venue, so every email to it can start from there. */
    @Operation(summary = "Set a location's contact",
            description = "Who to talk to at the venue: a name, an email address and a phone number, each optional. A full replacement: an omitted field is cleared.")
    @PutMapping("/locations/{locationId}/contact")
    Mono<LocationResponse> updateContact(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                         @Valid @RequestBody UpdateContactRequest request) {
        return locations.updateContact(user.id(), locationId, request);
    }

    @Operation(summary = "Delete a location")
    @DeleteMapping("/locations/{locationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId) {
        return locations.delete(user.id(), locationId);
    }
}
