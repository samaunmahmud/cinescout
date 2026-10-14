package com.cinescout.web;

import com.cinescout.agreements.ResidentsLetterService;
import com.cinescout.dto.ResidentsLetterRequest;
import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Tag(name = "Agreements")
class ResidentsLetterController {

    private final ResidentsLetterService letters;
    private final RateLimiter limits;

    ResidentsLetterController(ResidentsLetterService letters, RateLimiter limits) {
        this.letters = letters;
        this.limits = limits;
    }

    @Operation(summary = "Write the letter to a venue's neighbours, as PDF",
            description = "A one-page letter for the doors round the venue: who is filming, the scene's dates and hours, about how many "
                    + "people, parking, a night-work note when the scene runs late, and who to call. Made on request, not stored. The "
                    + "body is optional; the contact name defaults to the user's own. The venue owner's details and quote are never "
                    + "included.")
    @ApiResponse(responseCode = "200", description = "The PDF, as an attachment",
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "429", description = "The user's hourly allowance of lookups is used up; see Retry-After")
    @PostMapping(value = "/api/locations/{locationId}/residents-letter", produces = MediaType.APPLICATION_PDF_VALUE)
    Mono<ResponseEntity<byte[]>> letter(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                        @Valid @RequestBody(required = false) ResidentsLetterRequest request) {
        return limits.acquire(RateLimit.LOOKUPS, user.id())
                .then(Mono.defer(() -> letters.letter(user.id(), locationId, request)))
                .map(letter -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, LocationPackController.disposition(letter.filename()))
                        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                        .body(letter.content()));
    }
}
