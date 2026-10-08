package com.cinescout.web;

import com.cinescout.pack.LocationPackService;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@Tag(name = "Projects", description = "A user's projects.")
class LocationPackController {

    private final LocationPackService packs;
    private final RateLimiter limits;

    LocationPackController(LocationPackService packs, RateLimiter limits) {
        this.packs = packs;
        this.limits = limits;
    }

    @Operation(summary = "Download the production's location pack as PDF",
            description = "For each scene its confirmed venue, or its best three shortlisted while none is confirmed: photo, address and "
                    + "map link, contact and fee, the AI's read, booked days, recce answers and permit office. Counts as a lookup.")
    @ApiResponse(responseCode = "200", description = "The PDF, as an attachment",
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @GetMapping(value = "/api/projects/{projectId}/location-pack", produces = MediaType.APPLICATION_PDF_VALUE)
    Mono<ResponseEntity<byte[]>> pack(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return limits.acquire(RateLimit.LOOKUPS, user.id())
                .then(Mono.defer(() -> packs.pack(user.id(), projectId)))
                .map(pack -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, disposition(pack.filename()))
                        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                        .body(pack.content()));
    }

    /** An attachment named in plain ASCII for old clients and exactly (RFC 6266 filename*) for the rest. */
    static String disposition(String filename) {
        String ascii = filename.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "");
        String exact = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + exact;
    }
}
