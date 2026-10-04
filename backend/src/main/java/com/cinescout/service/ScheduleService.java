package com.cinescout.service;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.domain.VenueAvailability;
import com.cinescout.dto.ScheduleResponse;
import com.cinescout.dto.ScheduleResponse.ScheduledScene;
import com.cinescout.dto.ScheduleResponse.ShootDay;
import com.cinescout.permits.FilmingOffices;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.SceneRepository;
import com.cinescout.repository.VenueAvailabilityRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lays a project's scenes out by shoot day, each with the venue confirmed for it, so the gaps show: a scene
 * with a date and nowhere to shoot, or a venue and no date.
 */
@Service
public class ScheduleService {

    private final ProjectRepository projects;
    private final SceneRepository scenes;
    private final LocationRepository locations;
    private final VenueAvailabilityRepository availability;
    private final FilmingOffices offices;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public ScheduleService(ProjectRepository projects, SceneRepository scenes, LocationRepository locations,
                           VenueAvailabilityRepository availability, FilmingOffices offices, ProjectAccess access, BlockingTransactions db) {
        this.projects = projects;
        this.scenes = scenes;
        this.locations = locations;
        this.availability = availability;
        this.offices = offices;
        this.access = access;
        this.db = db;
    }

    /**
     * A scene is listed on the day its shoot starts (its last day, if that is all it has); a shoot window of
     * several days is one entry carrying both dates, not one a day. Each confirmed venue carries what is known of its
     * first shoot day, and the conflicts come with it, see {@link ScheduleConflicts}.
     */
    public Mono<ScheduleResponse> schedule(UUID userId, UUID projectId) {
        return db.call(() -> {
            access.project(userId, projectId, ProjectRole.VIEWER);
            Map<UUID, List<Location>> confirmed = locations
                    .findVisibleByProjectAndStatus(projectId, userId, LocationStatus.CONFIRMED, Pageable.unpaged())
                    .stream().collect(Collectors.groupingBy(location -> location.getScene().getId()));
            Map<UUID, Long> candidates = locations.countByScene(projectId).stream()
                    .collect(Collectors.toMap(LocationRepository.SceneCount::getSceneId, LocationRepository.SceneCount::getLocations));

            // In script order, so each day's scenes and the unscheduled ones come out in script order too.
            List<Scene> all = scenes.findVisibleByProject(projectId, userId, Pageable.unpaged()).getContent();
            ScheduleConflicts conflicts = new ScheduleConflicts(availability.findByProject(projectId), offices,
                    LocalDate.ofInstant(DatabaseTime.now(), ZoneOffset.UTC));
            Map<UUID, VenueAvailability> holds = conflicts.firstDays(all, confirmed);

            Map<LocalDate, List<ScheduledScene>> byDay = new TreeMap<>();
            List<ScheduledScene> unscheduled = new ArrayList<>();
            for (Scene scene : all) {
                ScheduledScene entry = ScheduledScene.from(scene, confirmed.getOrDefault(scene.getId(), List.of()),
                        candidates.getOrDefault(scene.getId(), 0L), holds);
                LocalDate day = scene.getShootDateStart() != null ? scene.getShootDateStart() : scene.getShootDateEnd();
                if (day == null) {
                    unscheduled.add(entry);
                } else {
                    byDay.computeIfAbsent(day, date -> new ArrayList<>()).add(entry);
                }
            }
            List<ShootDay> days = byDay.entrySet().stream().map(e -> new ShootDay(e.getKey(), e.getValue())).toList();
            return new ScheduleResponse(days, unscheduled, conflicts.find(all, confirmed));
        });
    }
}
