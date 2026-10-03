package com.cinescout.photos;

import com.cinescout.domain.LocationPhoto;
import com.cinescout.files.FileLinks;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A recce photo. {@code url} and {@code thumbUrl} are short-lived signed links (an hour or two): fetch the list again
 * for fresh ones. {@code latitude}/{@code longitude} are where it was taken, when the photo said; {@code cover} when
 * it is the one the venue's polaroid shows.
 */
public record PhotoResponse(
        UUID id,
        UUID locationId,
        String url,
        String thumbUrl,
        int width,
        int height,
        BigDecimal latitude,
        BigDecimal longitude,
        String uploadedBy,
        boolean cover,
        Instant createdAt
) {

    public static PhotoResponse from(LocationPhoto photo, UUID coverPhotoId, FileLinks links) {
        return new PhotoResponse(photo.getId(), photo.getLocation().getId(), links.photo(photo.getId(), "full"),
                links.photo(photo.getId(), "thumb"), photo.getWidth(), photo.getHeight(), photo.getGpsLatitude(), photo.getGpsLongitude(),
                photo.getUploadedBy() == null ? null : photo.getUploadedBy().getDisplayName(),
                photo.getId().equals(coverPhotoId), photo.getCreatedAt());
    }
}
