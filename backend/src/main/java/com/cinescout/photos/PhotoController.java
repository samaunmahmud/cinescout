package com.cinescout.photos;

import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.files.FileLinks;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.InvalidRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@Tag(name = "Recce photos", description = "Photos the crew took at a venue, kept without their metadata.")
class PhotoController {

    private final PhotoService photos;

    PhotoController(PhotoService photos) {
        this.photos = photos;
    }

    @Operation(summary = "List a venue's recce photos", description = "In the order they were added, each with short-lived links.")
    @GetMapping("/api/locations/{locationId}/photos")
    Mono<PageResponse<PhotoResponse>> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                           @Valid @ParameterObject PageQuery page) {
        return photos.list(user.id(), locationId, page);
    }

    @Operation(summary = "Add a recce photo to a venue",
            description = "Multipart, one `file`: a JPEG or PNG of up to 10 MB (the web app converts HEIC first and may send the "
                    + "`latitude`/`longitude` it read from it). It is re-encoded without its metadata; where it was taken is kept. "
                    + "At most " + PhotoService.MAX_PHOTOS + " a venue (409). `suggestPin` when the venue has no position and the photo has one.")
    @ApiResponse(responseCode = "413", description = "The file is larger than 10 MB")
    @PostMapping(value = "/api/locations/{locationId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    Mono<UploadResponse> upload(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                @RequestPart("file") Mono<FilePart> file,
                                @RequestPart(name = "latitude", required = false) String latitude,
                                @RequestPart(name = "longitude", required = false) String longitude) {
        GeoPoint reported = reportedGps(latitude, longitude);
        return file.flatMap(part -> DataBufferUtils.join(part.content(), PhotoService.MAX_BYTES))
                .map(buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .switchIfEmpty(Mono.error(new InvalidRequestException("file", "Choose a photo to upload")))
                .flatMap(bytes -> photos.upload(user.id(), locationId, bytes, reported));
    }

    @Operation(summary = "Delete a recce photo")
    @DeleteMapping("/api/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID photoId) {
        return photos.delete(user.id(), photoId);
    }

    @Operation(summary = "Choose the photo a venue's polaroid shows", description = "`photoId` null goes back to the picture from its web page.")
    @PutMapping("/api/locations/{locationId}/cover")
    Mono<LocationResponse> cover(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID locationId,
                                 @RequestBody CoverRequest request) {
        return photos.setCover(user.id(), locationId, request.photoId());
    }

    /** The link is the permission (see {@link FileLinks}); a wrong or expired one is simply not found. */
    @Operation(summary = "Fetch a photo by its signed link", description = "Public, for <img>: the links come from the photo list.")
    @SecurityRequirements
    @GetMapping("/api/public/photos/{photoId}")
    Mono<ResponseEntity<byte[]>> file(@PathVariable UUID photoId, @RequestParam String size, @RequestParam long exp,
                                      @RequestParam String sig) {
        return photos.file(photoId, size, exp, sig)
                .map(stored -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(stored.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofSeconds(Math.max(0,
                                FileLinks.expiry(exp).getEpochSecond() - Instant.now().getEpochSecond()))).cachePrivate())
                        .header("X-Content-Type-Options", "nosniff")
                        .header("X-Robots-Tag", "noindex")
                        .header("Content-Security-Policy", "default-src 'none'")
                        .body(stored.bytes()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private static GeoPoint reportedGps(String latitude, String longitude) {
        if (latitude == null || longitude == null || latitude.isBlank() || longitude.isBlank()) {
            return null;
        }
        try {
            double lat = Double.parseDouble(latitude.strip());
            double lng = Double.parseDouble(longitude.strip());
            return Math.abs(lat) <= 90 && Math.abs(lng) <= 180 && !(lat == 0 && lng == 0) ? new GeoPoint(lat, lng) : null;
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("latitude", "must be a number of degrees");
        }
    }
}
