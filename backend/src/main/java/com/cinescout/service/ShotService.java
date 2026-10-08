package com.cinescout.service;

import com.cinescout.domain.Location;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.domain.Shot;
import com.cinescout.dto.ShotMoveRequest;
import com.cinescout.dto.ShotRequest;
import com.cinescout.dto.ShotResponse;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsService;
import com.cinescout.logistics.solar.ShotLight;
import com.cinescout.logistics.solar.SolarCalculator;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.ShotRepository;
import com.cinescout.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A scene's shot list: viewers read it, editors change it. Every change answers with the whole list. */
@Service
public class ShotService {

    /** How many shots a scene may have. */
    static final int MAX_SHOTS = 150;

    private final ShotRepository shots;
    private final LocationRepository locations;
    private final UserRepository users;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public ShotService(ShotRepository shots, LocationRepository locations, UserRepository users, ProjectAccess access, BlockingTransactions db) {
        this.shots = shots;
        this.locations = locations;
        this.users = users;
        this.access = access;
        this.db = db;
    }

    public Mono<List<ShotResponse>> list(UUID userId, UUID sceneId) {
        return db.call(() -> list(access.scene(userId, sceneId, ProjectRole.VIEWER)));
    }

    public Mono<List<ShotResponse>> add(UUID userId, UUID sceneId, ShotRequest request) {
        return db.call(() -> {
            Scene scene = access.scene(userId, sceneId, ProjectRole.EDITOR);
            if (shots.countByScene(sceneId) >= MAX_SHOTS) {
                throw new ConflictException("A scene can have at most " + MAX_SHOTS + " shots; remove one first");
            }
            Shot shot = new Shot(scene, shots.lastPosition(sceneId) + 1, users.getReferenceById(userId));
            apply(shot, request, sceneId);
            shots.saveAndFlush(shot);
            return list(scene);
        });
    }

    public Mono<List<ShotResponse>> update(UUID userId, UUID shotId, ShotRequest request) {
        return db.call(() -> {
            Shot shot = find(userId, shotId);
            apply(shot, request, shot.getScene().getId());
            shots.saveAndFlush(shot);
            return list(shot.getScene());
        });
    }

    /** Swaps the shot with its neighbour; at either end it stays where it is. */
    public Mono<List<ShotResponse>> move(UUID userId, UUID shotId, ShotMoveRequest.Direction direction) {
        return db.call(() -> {
            Shot shot = find(userId, shotId);
            List<Shot> order = new ArrayList<>(shots.findByScene(shot.getScene().getId()));
            int at = order.indexOf(shot);
            int to = direction == ShotMoveRequest.Direction.EARLIER ? at - 1 : at + 1;
            if (at >= 0 && to >= 0 && to < order.size()) {
                order.set(at, order.get(to));
                order.set(to, shot);
            }
            for (int i = 0; i < order.size(); i++) {
                order.get(i).setPosition(i);
            }
            shots.saveAllAndFlush(order);
            return list(shot.getScene());
        });
    }

    public Mono<List<ShotResponse>> remove(UUID userId, UUID shotId) {
        return db.call(() -> {
            Shot shot = find(userId, shotId);
            Scene scene = shot.getScene();
            shots.delete(shot);
            shots.flush();
            return list(scene);
        });
    }

    private void apply(Shot shot, ShotRequest request, UUID sceneId) {
        shot.setDescription(request.description().strip());
        shot.setSize(request.size());
        shot.setCameraBearing(request.cameraBearing() == null ? null : request.cameraBearing().shortValue());
        shot.setPlannedTime(request.plannedTime());
        shot.setDone(Boolean.TRUE.equals(request.done()));
        shot.setLocation(request.locationId() == null ? null : locations.findById(request.locationId())
                .filter(venue -> venue.getScene().getId().equals(sceneId))
                .orElseThrow(() -> new InvalidRequestException("locationId", "Pick one of this scene's venues")));
    }

    /** A shot the user may change: 404 when it does not exist or the user is not on its project, 403 for a viewer. */
    private Shot find(UUID userId, UUID shotId) {
        Shot shot = shots.findWithScene(shotId).orElseThrow(() -> new NotFoundException("Shot", shotId));
        try {
            access.scene(userId, shot.getScene().getId(), ProjectRole.EDITOR);
        } catch (NotFoundException e) {
            throw new NotFoundException("Shot", shotId);
        }
        return shot;
    }

    private List<ShotResponse> list(Scene scene) {
        Location confirmed = locations.findConfirmedByScenes(List.of(scene.getId())).stream().findFirst().orElse(null);
        List<Shot> all = shots.findByScene(scene.getId());
        List<ShotResponse> out = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            Shot shot = all.get(i);
            Location venue = shot.getLocation() != null ? shot.getLocation() : confirmed;
            boolean isConfirmed = venue != null && confirmed != null && venue.getId().equals(confirmed.getId());
            String missing = missing(scene, shot, venue);
            out.add(ShotResponse.from(shot, i + 1, venue, isConfirmed, missing == null ? light(scene, shot, venue) : null, missing));
        }
        return out;
    }

    private static String missing(Scene scene, Shot shot, Location venue) {
        if (venue == null) {
            return "NO_VENUE";
        }
        if (venue.getLatitude() == null || venue.getLongitude() == null) {
            return "NO_PIN";
        }
        if (scene.getShootDateStart() == null) {
            return "NO_DATE";
        }
        if (shot.getPlannedTime() == null) {
            return "NO_TIME";
        }
        return LogisticsService.knownZone(venue.getLogisticsJson()) == null ? "NO_ZONE" : null;
    }

    private static ShotLight light(Scene scene, Shot shot, Location venue) {
        ZoneId zone = LogisticsService.knownZone(venue.getLogisticsJson());
        var at = ZonedDateTime.of(scene.getShootDateStart(), shot.getPlannedTime(), zone).toOffsetDateTime();
        var sun = SolarCalculator.position(at, new GeoPoint(venue.getLatitude().doubleValue(), venue.getLongitude().doubleValue()));
        return ShotLight.of(sun, shot.getCameraBearing() == null ? null : shot.getCameraBearing().intValue());
    }
}
