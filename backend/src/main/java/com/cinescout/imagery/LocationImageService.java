package com.cinescout.imagery;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.domain.Location;
import com.cinescout.dto.LocationResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.service.NotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * A picture for each venue, from its own web page, looked up once and kept. As elsewhere, the database work runs
 * in short transactions that never span the fetch.
 */
@Service
public class LocationImageService {

    private final PageImageFinder finder;
    private final LocationRepository locations;
    private final BlockingTransactions db;

    public LocationImageService(PageImageFinder finder, LocationRepository locations, BlockingTransactions db) {
        this.finder = finder;
        this.locations = locations;
        this.db = db;
    }

    /**
     * Looks the venue's picture up unless that has been done already, and returns the location.
     *
     * @throws NotFoundException (as an error signal) if the location is not the owner's
     */
    public Mono<LocationResponse> lookUp(UUID ownerId, UUID locationId) {
        return db.call(() -> owned(ownerId, locationId))
                .flatMap(location -> location.getImageCheckedAt() != null || location.getSourceUrl() == null
                        ? Mono.just(LocationResponse.from(location))
                        : finder.imageOf(location.getSourceUrl())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty())
                                .flatMap(image -> db.call(() -> {
                                    Location current = owned(ownerId, locationId);
                                    current.setImage(image.orElse(null), DatabaseTime.now());
                                    return LocationResponse.from(locations.saveAndFlush(current));
                                })));
    }

    private Location owned(UUID ownerId, UUID locationId) {
        return locations.findOwned(locationId, ownerId).orElseThrow(() -> new NotFoundException("Location", locationId));
    }
}
