package com.cinescout.imagery;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.domain.Location;
import com.cinescout.domain.ProjectRole;
import com.cinescout.dto.LocationResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.service.NotFoundException;
import com.cinescout.service.ProjectAccess;
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
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public LocationImageService(PageImageFinder finder, LocationRepository locations, ProjectAccess access, BlockingTransactions db) {
        this.finder = finder;
        this.locations = locations;
        this.access = access;
        this.db = db;
    }

    /**
     * Looks the venue's picture up unless that has been done already, and returns the location.
     *
     * @throws NotFoundException (as an error signal) if the location is not the owner's
     */
    public Mono<LocationResponse> lookUp(UUID userId, UUID locationId) {
        return db.call(() -> access.location(userId, locationId, ProjectRole.VIEWER))
                .flatMap(location -> location.getImageCheckedAt() != null || location.getSourceUrl() == null
                        ? Mono.just(LocationResponse.from(location))
                        : finder.imageOf(location.getSourceUrl())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty())
                                .flatMap(image -> db.call(() -> {
                                    Location current = access.location(userId, locationId, ProjectRole.VIEWER);
                                    current.setImage(image.orElse(null), DatabaseTime.now());
                                    return LocationResponse.from(locations.saveAndFlush(current));
                                })));
    }

}
