package com.cinescout.service;

import com.cinescout.domain.ActivityTarget;
import com.cinescout.domain.ActivityVerb;
import com.cinescout.domain.BookingFriction;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.dto.CreateLocationRequest;
import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.ProjectLocationResponse;
import com.cinescout.dto.UpdateContactRequest;
import com.cinescout.dto.UpdateCoordinatesRequest;
import com.cinescout.dto.UpdateLocationRequest;
import com.cinescout.export.LocationExport;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.SceneRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.cinescout.domain.LocationSort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
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
    private final ProjectRepository projects;
    private final ProjectAccess access;
    private final BlockingTransactions db;
    private final ActivityLog activity;

    public LocationService(LocationRepository locations, SceneRepository scenes, ProjectRepository projects, ProjectAccess access,
                           BlockingTransactions db, ActivityLog activity) {
        this.locations = locations;
        this.scenes = scenes;
        this.projects = projects;
        this.access = access;
        this.db = db;
        this.activity = activity;
    }

    /** Best fit first; venues without an assessment (added by hand) come last. */
    public Mono<PageResponse<LocationResponse>> list(UUID userId, UUID sceneId, PageQuery page) {
        return list(userId, sceneId, page, null, null);
    }

    /**
     * A scene's locations in the order asked for (null: best fit first), perhaps only those in one status (null: all).
     * Ties fall back to the oldest first, then the id, so a page boundary never shuffles.
     */
    public Mono<PageResponse<LocationResponse>> list(UUID userId, UUID sceneId, PageQuery page, LocationSort sort, LocationStatus status) {
        return db.call(() -> {
            access.scene(userId, sceneId, ProjectRole.VIEWER);
            if ((sort == null || sort == LocationSort.FIT) && status == null) {
                return PageResponse.from(locations.findVisibleByScene(sceneId, userId, page.pageable()), LocationResponse::from);
            }
            Pageable sorted = PageRequest.of(page.pageable().getPageNumber(), page.pageable().getPageSize(), order(sort));
            return PageResponse.from(locations.findVisibleBySceneSorted(sceneId, userId, status, sorted), LocationResponse::from);
        });
    }

    static Sort order(LocationSort sort) {
        Sort ties = JpaSort.unsafe(Sort.Direction.ASC, "l.createdAt", "l.id");
        return switch (sort == null ? LocationSort.FIT : sort) {
            case FIT -> JpaSort.unsafe(Sort.Direction.DESC, "coalesce(l.fitScore, -1)").and(ties);
            case NAME -> JpaSort.unsafe(Sort.Direction.ASC, "lower(l.name)").and(ties);
            case NEWEST -> JpaSort.unsafe(Sort.Direction.DESC, "l.createdAt", "l.id");
            // Wrapped in coalesce: Spring Data prefixes the alias to a bare expression, not to a function call.
            case STATUS -> JpaSort.unsafe(Sort.Direction.ASC, """
                    coalesce(case l.status when com.cinescout.domain.LocationStatus.CONFIRMED then 0
                                  when com.cinescout.domain.LocationStatus.CONTACTED then 1
                                  when com.cinescout.domain.LocationStatus.SHORTLISTED then 2
                                  when com.cinescout.domain.LocationStatus.SUGGESTED then 3 else 4 end, 4)""")
                    .and(JpaSort.unsafe(Sort.Direction.DESC, "coalesce(l.fitScore, -1)")).and(ties);
        };
    }

    /**
     * Every candidate location of a project: scene by scene in script order, best fit first within a scene.
     * {@code status} null means all.
     */
    public Mono<PageResponse<ProjectLocationResponse>> listForProject(UUID userId, UUID projectId, LocationStatus status, PageQuery page) {
        return db.call(() -> {
            access.project(userId, projectId, ProjectRole.VIEWER);
            return PageResponse.from(status == null
                    ? locations.findVisibleByProject(projectId, userId, page.pageable())
                    : locations.findVisibleByProjectAndStatus(projectId, userId, status, page.pageable()),
                    ProjectLocationResponse::from);
        });
    }

    /** The same list as {@link #listForProject}, all of it, as a spreadsheet. */
    public Mono<LocationExport> exportForProject(UUID userId, UUID projectId, LocationStatus status) {
        return db.call(() -> {
            Project project = access.project(userId, projectId, ProjectRole.VIEWER);
            return LocationExport.of(project, (status == null
                    ? locations.findVisibleByProject(projectId, userId, Pageable.unpaged())
                    : locations.findVisibleByProjectAndStatus(projectId, userId, status, Pageable.unpaged())).getContent());
        });
    }

    /** @throws ConflictException (as an error signal) if the scene already has a location with that URL */
    public Mono<LocationResponse> create(UUID userId, UUID sceneId, CreateLocationRequest request) {
        return db.call(() -> {
                    Scene scene = access.scene(userId, sceneId, ProjectRole.EDITOR);
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

    /** Sets who has to say yes to filming there (null clears it), over what scouting assessed. */
    public Mono<LocationResponse> setBookingRoute(UUID userId, UUID locationId, BookingFriction route) {
        return db.call(() -> {
            Location location = access.location(userId, locationId, ProjectRole.EDITOR);
            location.setBookingFriction(route);
            return LocationResponse.from(locations.saveAndFlush(location));
        });
    }

    public Mono<LocationResponse> get(UUID userId, UUID locationId) {
        return db.call(() -> LocationResponse.from(access.location(userId, locationId, ProjectRole.VIEWER)));
    }

    /** Full replacement of the user-owned workflow fields; a null note clears it. */
    public Mono<LocationResponse> update(UUID userId, UUID locationId, UpdateLocationRequest request) {
        return db.call(() -> {
            Location location = access.location(userId, locationId, ProjectRole.EDITOR);
            LocationStatus before = location.getStatus();
            location.setStatus(request.status());
            location.setNotes(blankToNull(request.notes()));
            if (request.status() != LocationStatus.REJECTED) {
                location.setRejectionReason(null);
            } else if (blankToNull(request.rejectionReason()) != null) {
                location.setRejectionReason(blankToNull(request.rejectionReason()));
            }
            if (before != request.status()) {
                activity.record(location.getScene().getProject(), userId, ActivityVerb.VENUE_STATUS_CHANGED, ActivityTarget.LOCATION,
                        location.getId(), ActivityLog.facts("venue", location.getName(), "scene", location.getScene().getTitle(),
                                "from", before.name(), "to", request.status().name()));
            }
            return LocationResponse.from(locations.saveAndFlush(location));
        });
    }

    /**
     * Sets where the venue is, e.g. because it could not be geocoded or the pin was wrong. Its cached
     * logistics are dropped, as they were for the old spot.
     */
    public Mono<LocationResponse> relocate(UUID userId, UUID locationId, UpdateCoordinatesRequest request) {
        return db.call(() -> {
            Location location = access.location(userId, locationId, ProjectRole.EDITOR);
            location.relocate(request.latitude(), request.longitude());
            return LocationResponse.from(locations.saveAndFlush(location));
        });
    }

    /** Full replacement of who to talk to at the venue; a null or blank field clears it. */
    public Mono<LocationResponse> updateContact(UUID userId, UUID locationId, UpdateContactRequest request) {
        return db.call(() -> {
            Location location = access.location(userId, locationId, ProjectRole.EDITOR);
            location.setContactName(blankToNull(request.name()));
            location.setContactEmail(blankToNull(request.email()));
            location.setContactPhone(blankToNull(request.phone()));
            location.setQuote(blankToNull(request.quote()));
            return LocationResponse.from(locations.saveAndFlush(location));
        });
    }

    public Mono<Void> delete(UUID userId, UUID locationId) {
        return db.run(() -> locations.delete(access.location(userId, locationId, ProjectRole.EDITOR)));
    }


    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
