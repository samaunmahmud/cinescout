package com.cinescout.service;

import com.cinescout.domain.Location;
import com.cinescout.domain.Scene;
import com.cinescout.dto.CreateLocationRequest;
import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.UpdateCoordinatesRequest;
import com.cinescout.dto.UpdateLocationRequest;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.SceneRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * The candidate locations of a scene: the user's own workflow on them (status, notes) and venues
 * they add by hand. The venues the scouting pipeline finds are saved by {@code SceneScoutingService}.
 */
@Service
public class LocationService {

    /** {@code source_provider} of a venue the user added themselves. */
    public static final String MANUAL_PROVIDER = "manual";

    private final LocationRepository locations;
    private final SceneRepository scenes;
    private final BlockingTransactions db;

    public LocationService(LocationRepository locations, SceneRepository scenes, BlockingTransactions db) {
        this.locations = locations;
        this.scenes = scenes;
        this.db = db;
    }

    /** Best fit first; venues without an assessment (added by hand) come last. */
    public Mono<PageResponse<LocationResponse>> list(UUID ownerId, UUID sceneId, PageQuery page) {
        return db.call(() -> {
            scenes.findOwned(sceneId, ownerId).orElseThrow(() -> new NotFoundException("Scene", sceneId));
            return PageResponse.from(locations.findOwnedByScene(sceneId, ownerId, page.pageable()), LocationResponse::from);
        });
    }

    /** @throws ConflictException (as an error signal) if the scene already has a location with that URL */
    public Mono<LocationResponse> create(UUID ownerId, UUID sceneId, CreateLocationRequest request) {
        return db.call(() -> {
                    Scene scene = scenes.findOwned(sceneId, ownerId).orElseThrow(() -> new NotFoundException("Scene", sceneId));
                    Location location = new Location(scene, request.name().strip());
                    location.setAddress(blankToNull(request.address()));
                    location.setLatitude(request.latitude());
                    location.setLongitude(request.longitude());
                    location.setSourceUrl(blankToNull(request.sourceUrl()));
                    location.setSourceProvider(MANUAL_PROVIDER);
                    location.setNotes(blankToNull(request.notes()));
                    return LocationResponse.from(locations.saveAndFlush(location));
                })
                .onErrorMap(DataIntegrityViolationException.class, Conflicts::translate);
    }

    public Mono<LocationResponse> get(UUID ownerId, UUID locationId) {
        return db.call(() -> LocationResponse.from(owned(ownerId, locationId)));
    }

    /** Full replacement of the user-owned workflow fields; a null note clears it. */
    public Mono<LocationResponse> update(UUID ownerId, UUID locationId, UpdateLocationRequest request) {
        return db.call(() -> {
            Location location = owned(ownerId, locationId);
            location.setStatus(request.status());
            location.setNotes(blankToNull(request.notes()));
            return LocationResponse.from(locations.saveAndFlush(location));
        });
    }

    /**
     * Sets where the venue is, e.g. because it could not be geocoded or the pin was wrong. Its cached
     * logistics are dropped, as they were for the old spot.
     */
    public Mono<LocationResponse> relocate(UUID ownerId, UUID locationId, UpdateCoordinatesRequest request) {
        return db.call(() -> {
            Location location = owned(ownerId, locationId);
            location.relocate(request.latitude(), request.longitude());
            return LocationResponse.from(locations.saveAndFlush(location));
        });
    }

    public Mono<Void> delete(UUID ownerId, UUID locationId) {
        return db.run(() -> locations.delete(owned(ownerId, locationId)));
    }

    private Location owned(UUID ownerId, UUID locationId) {
        return locations.findOwned(locationId, ownerId).orElseThrow(() -> new NotFoundException("Location", locationId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
