package com.cinescout.service;

import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.Scene;
import com.cinescout.dto.ScheduleResponse;
import com.cinescout.dto.ScheduleResponse.ScheduledScene;
import com.cinescout.dto.ScheduleResponse.ShootDay;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.SceneRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
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
    private final BlockingTransactions db;

    public ScheduleService(ProjectRepository projects, SceneRepository scenes, LocationRepository locations, BlockingTransactions db) {
        this.projects = projects;
        this.scenes = scenes;
        this.locations = locations;
        this.db = db;
    }

    /**
     * A scene is listed on the day its shoot starts (its last day, if that is all it has); a shoot window of
     * several days is one entry carrying both dates, not one a day.
     */
    public Mono<ScheduleResponse> schedule(UUID ownerId, UUID projectId) {
        return db.call(() -> {
            projects.findByIdAndOwnerId(projectId, ownerId).orElseThrow(() -> new NotFoundException("Project", projectId));
            Map<UUID, List<Location>> confirmed = locations
                    .findOwnedByProjectAndStatus(projectId, ownerId, LocationStatus.CONFIRMED, Pageable.unpaged())
                    .stream().collect(Collectors.groupingBy(location -> location.getScene().getId()));
            Map<UUID, Long> candidates = locations.countByScene(projectId).stream()
                    .collect(Collectors.toMap(LocationRepository.SceneCount::getSceneId, LocationRepository.SceneCount::getLocations));

            Map<LocalDate, List<ScheduledScene>> byDay = new TreeMap<>();
            List<ScheduledScene> unscheduled = new ArrayList<>();
            // In script order, so each day's scenes and the unscheduled ones come out in script order too.
            for (Scene scene : scenes.findOwnedByProject(projectId, ownerId, Pageable.unpaged())) {
                ScheduledScene entry = ScheduledScene.from(scene, confirmed.getOrDefault(scene.getId(), List.of()),
                        candidates.getOrDefault(scene.getId(), 0L));
                LocalDate day = scene.getShootDateStart() != null ? scene.getShootDateStart() : scene.getShootDateEnd();
                if (day == null) {
                    unscheduled.add(entry);
                } else {
                    byDay.computeIfAbsent(day, date -> new ArrayList<>()).add(entry);
                }
            }
            List<ShootDay> days = byDay.entrySet().stream().map(e -> new ShootDay(e.getKey(), e.getValue())).toList();
            return new ScheduleResponse(days, unscheduled);
        });
    }
}
