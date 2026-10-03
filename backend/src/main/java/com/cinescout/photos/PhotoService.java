package com.cinescout.photos;

import com.cinescout.domain.Location;
import com.cinescout.domain.LocationPhoto;
import com.cinescout.domain.ProjectRole;
import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.files.FileLinks;
import com.cinescout.files.FileStore;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationPhotoRepository;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.UserRepository;
import com.cinescout.service.ConflictException;
import com.cinescout.service.InvalidRequestException;
import com.cinescout.service.NotFoundException;
import com.cinescout.service.ProjectAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Recce photos of a venue: taken up by editors (re-encoded without metadata, see {@link ImageProcessor}), kept in
 * the {@link FileStore}, listed for the crew with short-lived links, one chosen as the venue's cover.
 */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    public static final int MAX_PHOTOS = 30;
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    private final LocationPhotoRepository photos;
    private final LocationRepository locations;
    private final UserRepository users;
    private final ProjectAccess access;
    private final FileStore files;
    private final FileLinks links;
    private final BlockingTransactions db;

    public PhotoService(LocationPhotoRepository photos, LocationRepository locations, UserRepository users, ProjectAccess access,
                        FileStore files, FileLinks links, BlockingTransactions db) {
        this.photos = photos;
        this.locations = locations;
        this.users = users;
        this.access = access;
        this.files = files;
        this.links = links;
        this.db = db;
    }

    public Mono<PageResponse<PhotoResponse>> list(UUID userId, UUID locationId, PageQuery page) {
        return db.call(() -> {
            Location location = access.location(userId, locationId, ProjectRole.VIEWER);
            return PageResponse.from(photos.findByLocation(locationId, page.pageable()),
                    photo -> PhotoResponse.from(photo, location.getCoverPhotoId(), links));
        });
    }

    /**
     * Takes up a photo. {@code reportedGps} is where the browser read it was taken, for a photo it had to convert
     * (HEIC) and so lost the metadata of; a position in the photo itself wins.
     */
    public Mono<UploadResponse> upload(UUID userId, UUID locationId, byte[] upload, GeoPoint reportedGps) {
        UUID id = UUID.randomUUID();
        return db.call(() -> {
                    room(access.location(userId, locationId, ProjectRole.EDITOR));
                    return Boolean.TRUE;
                })
                .then(Mono.fromCallable(() -> ImageProcessor.process(upload)).subscribeOn(Schedulers.boundedElastic())
                        .onErrorMap(UnreadableImageException.class, e -> new InvalidRequestException("file", e.getMessage())))
                .flatMap(processed -> {
                    String extension = processed.contentType().equals("image/png") ? ".png" : ".jpg";
                    String key = "photos/" + locationId + "/" + id + extension;
                    String thumbKey = "photos/" + locationId + "/" + id + "-thumb.jpg";
                    GeoPoint gps = processed.gps() != null ? processed.gps() : reportedGps;
                    return files.put(key, processed.full(), processed.contentType())
                            .then(files.put(thumbKey, processed.thumb(), "image/jpeg"))
                            .then(db.call(() -> {
                                Location location = access.location(userId, locationId, ProjectRole.EDITOR);
                                room(location);
                                LocationPhoto photo = photos.saveAndFlush(new LocationPhoto(id, location, users.getReferenceById(userId),
                                        key, thumbKey, processed.contentType(), processed.width(), processed.height(),
                                        processed.full().length, degrees(gps == null ? null : gps.latitude()),
                                        degrees(gps == null ? null : gps.longitude())));
                                boolean suggestPin = gps != null && (location.getLatitude() == null || location.getLongitude() == null);
                                return new UploadResponse(PhotoResponse.from(photo, location.getCoverPhotoId(), links), suggestPin);
                            }))
                            .onErrorResume(error -> discard(key, thumbKey).then(Mono.error(error)));
                });
    }

    /** Removes a photo and its files; the venue's cover goes back to its web page's picture if it was this one. */
    public Mono<Void> delete(UUID userId, UUID photoId) {
        return db.call(() -> {
                    LocationPhoto photo = visible(userId, photoId);
                    access.location(userId, photo.getLocation().getId(), ProjectRole.EDITOR);
                    Location location = photo.getLocation();
                    if (photoId.equals(location.getCoverPhotoId())) {
                        location.setCoverPhotoId(null);
                        locations.saveAndFlush(location);
                    }
                    photos.delete(photo);
                    photos.flush();
                    return new String[] {photo.getStorageKey(), photo.getThumbKey()};
                })
                .flatMap(keys -> discard(keys[0], keys[1]));
    }

    /** Sets which photo the venue's polaroid shows, or none. */
    public Mono<LocationResponse> setCover(UUID userId, UUID locationId, UUID photoId) {
        return db.call(() -> {
            Location location = access.location(userId, locationId, ProjectRole.EDITOR);
            if (photoId != null && photos.findById(photoId).filter(p -> p.getLocation().getId().equals(locationId)).isEmpty()) {
                throw new InvalidRequestException("photoId", "That photo is not one of this venue's");
            }
            location.setCoverPhotoId(photoId);
            return LocationResponse.from(locations.saveAndFlush(location));
        });
    }

    /** A photo's bytes for a signed link; empty when the link is not valid or the photo is gone. */
    public Mono<StoredPhoto> file(UUID photoId, String size, long expires, String sig) {
        if (!("full".equals(size) || "thumb".equals(size)) || !links.valid(photoId, size, expires, sig)) {
            return Mono.empty();
        }
        return db.call(() -> photos.findById(photoId)
                        .map(photo -> "thumb".equals(size)
                                ? new StoredPhoto(photo.getThumbKey(), "image/jpeg", null)
                                : new StoredPhoto(photo.getStorageKey(), photo.getContentType(), null))
                        .orElse(null))
                .flatMap(stored -> files.get(stored.key()).map(bytes -> new StoredPhoto(stored.key(), stored.contentType(), bytes)));
    }

    public record StoredPhoto(String key, String contentType, byte[] bytes) {
    }

    private LocationPhoto visible(UUID userId, UUID photoId) {
        LocationPhoto photo = photos.findWithProject(photoId).orElseThrow(() -> new NotFoundException("Photo", photoId));
        try {
            access.location(userId, photo.getLocation().getId(), ProjectRole.VIEWER);
        } catch (NotFoundException e) {
            throw new NotFoundException("Photo", photoId);
        }
        return photo;
    }

    private void room(Location location) {
        if (photos.countByLocationId(location.getId()) >= MAX_PHOTOS) {
            throw new ConflictException("A venue keeps at most " + MAX_PHOTOS + " photos; delete one to add another");
        }
    }

    private Mono<Void> discard(String key, String thumbKey) {
        return files.delete(key).then(files.delete(thumbKey))
                .onErrorResume(error -> {
                    log.warn("Could not remove the files of photo {}: {}", key, error.toString());
                    return Mono.empty();
                });
    }

    private static BigDecimal degrees(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }
}
