package com.cinescout.web;

import com.cinescout.dto.PlaceResponse;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.geocoding.Geocoder;
import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Validated
@Tag(name = "Places", description = "Names for spots on the map, e.g. the browser's current position.")
class PlaceController {

    private final Geocoder geocoder;
    private final RateLimiter limits;

    PlaceController(Geocoder geocoder, RateLimiter limits) {
        this.geocoder = geocoder;
        this.limits = limits;
    }

    @Operation(summary = "Name a spot",
            description = "The neighbourhood, town and country a position is in, named in the languages of the Accept-Language header, e.g. to fill a project's location area from the "
                    + "browser's current position. Counts as a lookup.")
    @ApiResponse(responseCode = "404", description = "The map service has no name for the spot (out at sea, say)")
    @ApiResponse(responseCode = "429", description = "The user's hourly allowance of lookups is used up; see Retry-After")
    @ApiResponse(responseCode = "503", description = "The map service is busy or unavailable; see Retry-After")
    @GetMapping("/api/places/here")
    Mono<PlaceResponse> here(@AuthenticationPrincipal AuthenticatedUser user,
                             @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
                             @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
                             @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String languages) {
        return limits.acquire(RateLimit.LOOKUPS, user.id())
                .then(Mono.defer(() -> geocoder.placeAt(new GeoPoint(lat, lng), languages)))
                .map(name -> new PlaceResponse(name, lat, lng, geocoder.attribution()))
                .switchIfEmpty(Mono.error(() -> new NotFoundException("The map has no name for that spot")));
    }
}
