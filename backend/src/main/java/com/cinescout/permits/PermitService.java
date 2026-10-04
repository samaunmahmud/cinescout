package com.cinescout.permits;

import com.cinescout.domain.AdminArea;
import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.Location;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.dto.PermitGuidance;
import com.cinescout.dto.PermitGuidance.Status;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.geocoding.Geocoder;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.ratelimit.RateLimitExceededException;
import com.cinescout.repository.LocationRepository;
import com.cinescout.service.ProjectAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The permit guide for a venue: the area its position falls in (looked up once, then kept on the venue until it
 * moves) matched against {@link FilmingOffices}.
 */
@Service
public class PermitService {

    private static final Logger log = LoggerFactory.getLogger(PermitService.class);

    private final FilmingOffices offices;
    private final Geocoder geocoder;
    private final LocationRepository locations;
    private final ProjectAccess access;
    private final BlockingTransactions db;
    private final String editUrl;

    PermitService(FilmingOffices offices, Geocoder geocoder, LocationRepository locations, ProjectAccess access, BlockingTransactions db,
                  PermitsConfig.PermitsProperties props) {
        this.offices = offices;
        this.geocoder = geocoder;
        this.locations = locations;
        this.access = access;
        this.db = db;
        this.editUrl = props.editUrl() == null || props.editUrl().isBlank() ? null : props.editUrl();
    }

    /**
     * @param permit asked for before the geocoder is called (the user's allowance of lookups); not when the area is
     *               already known
     */
    public Mono<PermitGuidance> guidance(UUID userId, UUID locationId, Supplier<Mono<Void>> permit) {
        return db.call(() -> Snapshot.of(access.location(userId, locationId, ProjectRole.VIEWER)))
                .flatMap(venue -> {
                    if (venue.point() == null) {
                        return Mono.just(respond(venue, Status.NEEDS_POSITION, null));
                    }
                    if (venue.area() != null && venue.area().isFor(venue.point().latitude(), venue.point().longitude())) {
                        return Mono.just(respond(venue, null, venue.area()));
                    }
                    return permit.get()
                            .then(geocoder.areaAt(venue.point()))
                            .flatMap(area -> db.call(() -> {
                                Location location = access.location(userId, locationId, ProjectRole.VIEWER);
                                location.setAdminArea(area);
                                locations.saveAndFlush(location);
                                return respond(venue, null, area);
                            }))
                            .switchIfEmpty(Mono.fromSupplier(() -> respond(venue, Status.LOOKUP_FAILED, null)))
                            .onErrorResume(error -> !(error instanceof RateLimitExceededException), error -> {
                                log.info("Could not look up the area of venue {}: {}", locationId, error.toString());
                                return Mono.just(respond(venue, Status.LOOKUP_FAILED, null));
                            });
                });
    }

    private PermitGuidance respond(Snapshot venue, Status forced, AdminArea area) {
        List<PermitGuidance.Source> sources = offices.sources().stream().map(s -> new PermitGuidance.Source(s.name(), s.url())).toList();
        boolean applies = venue.friction() == BookingFriction.PUBLIC;
        String areaName = area == null ? null : area.name();
        Status status = forced;
        PermitGuidance.Office office = null;
        if (status == null) {
            var listed = offices.officeFor(area);
            if (listed.isPresent()) {
                status = Status.FOUND;
                var found = listed.get();
                office = new PermitGuidance.Office(found.area(), found.office(), found.contactUrl(), found.leadTimeFor(venue.crewSize()),
                        found.leadTimeText(), null, offices.checklistFor(found), true);
            } else {
                status = "gb".equals(area.countryCode()) ? Status.FALLBACK : Status.OUTSIDE_COVERAGE;
            }
        }
        if (status == Status.FALLBACK || status == Status.LOOKUP_FAILED) {
            FilmingOffices.Fallback fallback = offices.fallback();
            office = new PermitGuidance.Office(fallback.area(), fallback.office(), fallback.contactUrl(), null, null, fallback.note(),
                    offices.checklistFor(null), false);
        }
        return new PermitGuidance(status, applies, areaName, office, offices.lastReviewed(), sources, editUrl);
    }

    /** What the guide needs of a venue, read in its transaction. */
    private record Snapshot(GeoPoint point, AdminArea area, BookingFriction friction, Integer crewSize) {

        static Snapshot of(Location location) {
            GeoPoint point = location.getLatitude() == null || location.getLongitude() == null ? null
                    : new GeoPoint(location.getLatitude().doubleValue(), location.getLongitude().doubleValue());
            SceneRequirements needs = location.getScene().requirements();
            return new Snapshot(point, location.getAdminArea(), location.getBookingFriction(),
                    needs == null ? null : needs.estimatedCastAndCrewSize());
        }
    }
}
