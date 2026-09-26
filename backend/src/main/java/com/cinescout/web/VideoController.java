package com.cinescout.web;

import com.cinescout.security.AuthenticatedUser;
import com.cinescout.video.LocationVideos;
import com.cinescout.video.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Videos of a venue from YouTube. Only exists when the YouTube key is configured; 503 otherwise. */
@RestController
@Tag(name = "Videos", description = "Videos of a venue, to see the place before a recce.")
class VideoController {

    private final ObjectProvider<VideoService> videos;

    VideoController(ObjectProvider<VideoService> videos) {
        this.videos = videos;
    }

    @Operation(summary = "Videos of a location",
            description = "Searches for the venue's name and address (or the project's area). Results are cached on the "
                    + "location for a week, or until its name or address changes. Each video's id is 11 characters of "
                    + "[A-Za-z0-9_-]; build thumbnail and player URLs from it.")
    @ApiResponse(responseCode = "503", description = "Videos are not configured on this server, the provider is down, or its daily quota is used up")
    @ApiResponse(responseCode = "502", description = "The provider rejected the server's API key")
    @GetMapping("/api/locations/{locationId}/videos")
    Mono<LocationVideos> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId) {
        return Mono.defer(() -> {
            VideoService service = videos.getIfAvailable();
            return service == null
                    ? Mono.error(new FeatureUnavailableException("Videos are not configured on this server"))
                    : service.videos(user.id(), locationId);
        });
    }
}
