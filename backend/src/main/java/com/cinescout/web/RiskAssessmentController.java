package com.cinescout.web;

import com.cinescout.agreements.RiskAssessmentService;
import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Tag(name = "Agreements")
class RiskAssessmentController {

    private final RiskAssessmentService assessments;
    private final RateLimiter limits;

    RiskAssessmentController(RiskAssessmentService assessments, RateLimiter limits) {
        this.assessments = assessments;
        this.limits = limits;
    }

    @Operation(summary = "Draft a risk assessment for a venue, as PDF",
            description = "Hazards picked from the scene (exterior, night, crew size, the kind of place) and the tech recce (power, "
                    + "ceiling height, stairs, toilets, phone signal, step-free access), each with why it is there and suggested "
                    + "controls; the nearest hospital and weather warnings from the cached logistics report; blanks to rate each "
                    + "risk and sign off. A draft for a competent person to complete on site. Made on request, not stored.")
    @ApiResponse(responseCode = "200", description = "The PDF, as an attachment",
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "429", description = "The user's hourly allowance of lookups is used up; see Retry-After")
    @GetMapping(value = "/api/locations/{locationId}/risk-assessment", produces = MediaType.APPLICATION_PDF_VALUE)
    Mono<ResponseEntity<byte[]>> assessment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId) {
        return limits.acquire(RateLimit.LOOKUPS, user.id())
                .then(Mono.defer(() -> assessments.assessment(user.id(), locationId)))
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, LocationPackController.disposition(file.filename()))
                        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                        .body(file.content()));
    }
}
