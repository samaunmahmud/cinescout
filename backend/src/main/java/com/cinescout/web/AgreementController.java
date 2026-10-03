package com.cinescout.web;

import com.cinescout.agreements.AgreementService;
import com.cinescout.dto.AgreementRequest;
import com.cinescout.dto.AgreementResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.security.AuthenticatedUser;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

@RestController
@Tag(name = "Agreements", description = "Location releases filled in from a venue's details: templates, not legal advice.")
class AgreementController {

    private final AgreementService agreements;
    private final RateLimiter limits;

    AgreementController(AgreementService agreements, RateLimiter limits) {
        this.agreements = agreements;
        this.limits = limits;
    }

    @Operation(summary = "List a venue's location releases, newest version first")
    @GetMapping("/api/locations/{locationId}/agreements")
    Mono<PageResponse<AgreementResponse>> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                               @Valid @ParameterObject PageQuery page) {
        return agreements.list(user.id(), locationId, page);
    }

    @Operation(summary = "Generate a new version of a venue's location release",
            description = "A PDF template filled in from the production, the venue, its contact and quote, the scene's shoot dates, call "
                    + "and wrap times and estimated crew size; blanks where something is not known. Every page says it is a template "
                    + "and not legal advice. The body is optional.")
    @ApiResponse(responseCode = "201", description = "Created; the Location header points at the PDF")
    @ApiResponse(responseCode = "409", description = "The venue already has 30 versions")
    @ApiResponse(responseCode = "429", description = "The user's hourly allowance of lookups is used up; see Retry-After")
    @PostMapping("/api/locations/{locationId}/agreements")
    Mono<ResponseEntity<AgreementResponse>> generate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                                     @Valid @RequestBody(required = false) AgreementRequest request) {
        return limits.acquire(RateLimit.LOOKUPS, user.id())
                .then(Mono.defer(() -> agreements.generate(user.id(), locationId, request)))
                .map(created -> ResponseEntity.created(URI.create("/api/agreements/" + created.id() + "/file")).body(created));
    }

    @Operation(summary = "Download a location release as PDF")
    @ApiResponse(responseCode = "200", description = "The PDF, as an attachment",
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @GetMapping(value = "/api/agreements/{agreementId}/file", produces = MediaType.APPLICATION_PDF_VALUE)
    Mono<ResponseEntity<byte[]>> file(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID agreementId) {
        return agreements.file(user.id(), agreementId)
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                        .body(file.content()));
    }

    @Operation(summary = "Delete a version of a location release")
    @DeleteMapping("/api/agreements/{agreementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID agreementId) {
        return agreements.delete(user.id(), agreementId);
    }
}
