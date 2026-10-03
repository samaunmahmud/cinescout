package com.cinescout.web;

import com.cinescout.dto.AvailabilityRequest;
import com.cinescout.dto.AvailabilityResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Locations", description = "Candidate venues for a scene: found by scouting or added by hand.")
class AvailabilityController {

    private final AvailabilityService availability;

    AvailabilityController(AvailabilityService availability) {
        this.availability = availability;
    }

    @Operation(summary = "List a venue's holds and availability, earliest day first")
    @GetMapping("/api/locations/{locationId}/availability")
    Mono<PageResponse<AvailabilityResponse>> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                                  @Valid @ParameterObject PageQuery page) {
        return availability.list(user.id(), locationId, page);
    }

    @Operation(summary = "Set a venue's state on a day or a run of days",
            description = "PENCILLED, HELD, CONFIRMED or UNAVAILABLE, each day replaced as a whole. A pencil or hold may lapse on "
                    + "holdExpiresOn. Up to 62 days at once. The schedule warns when a confirmed venue is unavailable on a shoot day "
                    + "or its hold lapses before it.")
    @PutMapping("/api/locations/{locationId}/availability")
    Mono<List<AvailabilityResponse>> set(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                         @Valid @RequestBody AvailabilityRequest request) {
        return availability.set(user.id(), locationId, request);
    }

    @Operation(summary = "Forget a venue's state on a day")
    @DeleteMapping("/api/locations/{locationId}/availability/{day}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> clear(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                     @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return availability.clear(user.id(), locationId, day);
    }
}
