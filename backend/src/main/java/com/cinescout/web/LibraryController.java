package com.cinescout.web;

import com.cinescout.dto.AddFromLibraryRequest;
import com.cinescout.dto.LibraryVenueResponse;
import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.SaveToLibraryRequest;
import com.cinescout.dto.TagCountResponse;
import com.cinescout.dto.UpdateLibraryVenueRequest;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.InvalidRequestException;
import com.cinescout.service.LibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@Tag(name = "Location library", description = "Each user's own saved venues, to reuse in any scene without scouting again.")
class LibraryController {

    private final LibraryService library;

    LibraryController(LibraryService library) {
        this.library = library;
    }

    @Operation(summary = "List the user's library",
            description = "Newest first, or nearest first when `nearLat` and `nearLng` are given (each venue then carries "
                    + "`distanceKm`; venues without a position come last). `q` matches the name, address, notes or a tag; "
                    + "`tag` keeps the venues with that tag.")
    @GetMapping("/api/library")
    Mono<PageResponse<LibraryVenueResponse>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @RequestParam(required = false) String q, @RequestParam(required = false) String tag,
                                                  @RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") Double nearLat,
                                                  @RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") Double nearLng,
                                                  @Valid @ParameterObject PageQuery page) {
        if ((nearLat == null) != (nearLng == null)) {
            throw new InvalidRequestException("nearLat", "nearLat and nearLng must be given together");
        }
        return library.list(user.id(), q, tag, nearLat == null ? null : new GeoPoint(nearLat, nearLng), page);
    }

    @Operation(summary = "List the tags in the user's library", description = "Most used first, with how many venues carry each; at most 100.")
    @GetMapping("/api/library/tags")
    Mono<List<TagCountResponse>> tags(@AuthenticationPrincipal AuthenticatedUser user) {
        return library.tags(user.id());
    }

    @Operation(summary = "Save a venue to the user's library",
            description = "From any venue the user can see. 201 with the new copy; 200 with the copy already saved from that venue.")
    @PostMapping("/api/library")
    Mono<ResponseEntity<LibraryVenueResponse>> save(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @Valid @RequestBody SaveToLibraryRequest request) {
        return library.save(user.id(), request.locationId())
                .map(saved -> ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK).body(saved.venue()));
    }

    @Operation(summary = "Get a venue from the user's library")
    @GetMapping("/api/library/{venueId}")
    Mono<LibraryVenueResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID venueId) {
        return library.get(user.id(), venueId);
    }

    @Operation(summary = "Rename, tag or annotate a library venue", description = "A full replacement of name, tags and notes.")
    @PutMapping("/api/library/{venueId}")
    Mono<LibraryVenueResponse> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID venueId,
                                      @Valid @RequestBody UpdateLibraryVenueRequest request) {
        return library.update(user.id(), venueId, request);
    }

    @Operation(summary = "Remove a venue from the user's library", description = "Venues copied into scenes from it stay.")
    @DeleteMapping("/api/library/{venueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID venueId) {
        return library.delete(user.id(), venueId);
    }

    @Operation(summary = "Add a library venue to a scene",
            description = "Copies it in as a venue added without scouting (no AI call): place, picture, booking facts and contact. "
                    + "Editors and the owner of the scene's project. 409 if the scene already has a venue from the same page.")
    @PostMapping("/api/scenes/{sceneId}/locations/from-library")
    @ResponseStatus(HttpStatus.CREATED)
    Mono<LocationResponse> addToScene(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                                      @Valid @RequestBody AddFromLibraryRequest request) {
        return library.addToScene(user.id(), sceneId, request.libraryVenueId());
    }
}
