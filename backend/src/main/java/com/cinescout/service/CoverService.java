package com.cinescout.service;

import com.cinescout.domain.ActivityTarget;
import com.cinescout.domain.ActivityVerb;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.domain.SceneCover;
import com.cinescout.dto.CoverRequest;
import com.cinescout.dto.CoverResponse;
import com.cinescout.dto.CoverTriggerRequest;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.SceneCoverRepository;
import com.cinescout.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Cover sets: a scene's backup venues, each one of its own candidates, with when to switch to it. Read by the crew,
 * set by editors and the owner.
 */
@Service
public class CoverService {

    /** How many covers a scene may have. */
    static final int MAX_COVERS = 5;

    private final SceneCoverRepository covers;
    private final LocationRepository locations;
    private final UserRepository users;
    private final ProjectAccess access;
    private final BlockingTransactions db;
    private final ActivityLog activity;

    public CoverService(SceneCoverRepository covers, LocationRepository locations, UserRepository users, ProjectAccess access,
                        BlockingTransactions db, ActivityLog activity) {
        this.covers = covers;
        this.locations = locations;
        this.users = users;
        this.access = access;
        this.db = db;
        this.activity = activity;
    }

    /** In the order they were added. */
    public Mono<PageResponse<CoverResponse>> list(UUID userId, UUID sceneId, PageQuery page) {
        return db.call(() -> {
            access.scene(userId, sceneId, ProjectRole.VIEWER);
            return PageResponse.from(covers.findByScene(sceneId, page.pageable()), CoverResponse::from);
        });
    }

    public Mono<CoverResponse> add(UUID userId, UUID sceneId, CoverRequest request) {
        return db.call(() -> {
            Scene scene = access.scene(userId, sceneId, ProjectRole.EDITOR);
            Location venue = locations.findById(request.locationId())
                    .filter(location -> location.getScene().getId().equals(sceneId))
                    .orElseThrow(() -> new InvalidRequestException("locationId", "Pick one of this scene's candidate venues"));
            if (venue.getStatus() == LocationStatus.CONFIRMED) {
                throw new ConflictException(venue.getName() + " is confirmed for this scene; a cover set is the backup to it");
            }
            if (covers.exists(sceneId, venue.getId())) {
                throw new ConflictException(venue.getName() + " is already a cover set for this scene");
            }
            if (covers.countByScene(sceneId) >= MAX_COVERS) {
                throw new ConflictException("A scene can have at most " + MAX_COVERS + " cover sets; remove one first");
            }
            SceneCover cover = covers.saveAndFlush(new SceneCover(scene, venue, clean(request.trigger()), users.getReferenceById(userId)));
            record(userId, cover, "ADDED");
            return CoverResponse.from(covers.findWithScene(cover.getId()).orElseThrow());
        });
    }

    public Mono<CoverResponse> setTrigger(UUID userId, UUID coverId, CoverTriggerRequest request) {
        return db.call(() -> {
            SceneCover cover = find(userId, coverId);
            cover.setTrigger(clean(request.trigger()));
            covers.saveAndFlush(cover);
            record(userId, cover, "CHANGED");
            return CoverResponse.from(covers.findWithScene(coverId).orElseThrow());
        });
    }

    /** The venue stays a candidate of the scene; it is only no longer its cover. */
    public Mono<Void> remove(UUID userId, UUID coverId) {
        return db.run(() -> {
            SceneCover cover = find(userId, coverId);
            covers.delete(cover);
            record(userId, cover, "REMOVED");
        });
    }

    /** A cover the user may change: 404 when it does not exist or the user is not on its project, 403 for a viewer. */
    private SceneCover find(UUID userId, UUID coverId) {
        SceneCover cover = covers.findWithScene(coverId).orElseThrow(() -> new NotFoundException("Cover set", coverId));
        try {
            access.scene(userId, cover.getScene().getId(), ProjectRole.EDITOR);
        } catch (NotFoundException e) {
            throw new NotFoundException("Cover set", coverId);
        }
        return cover;
    }

    private void record(UUID userId, SceneCover cover, String change) {
        activity.record(cover.getScene().getProject(), userId, ActivityVerb.COVER_SET_CHANGED, ActivityTarget.SCENE, cover.getScene().getId(),
                ActivityLog.facts("scene", cover.getScene().getTitle(), "venue", cover.getLocation().getName(), "change", change));
    }

    private static String clean(String trigger) {
        return trigger == null || trigger.isBlank() ? null : trigger.strip();
    }
}
