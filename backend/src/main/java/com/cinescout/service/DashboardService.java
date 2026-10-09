package com.cinescout.service;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.Scene;
import com.cinescout.dto.ActivityResponse;
import com.cinescout.dto.DashboardResponse;
import com.cinescout.dto.DashboardResponse.FreshFind;
import com.cinescout.dto.DashboardResponse.Happening;
import com.cinescout.dto.DashboardResponse.ShootDay;
import com.cinescout.dto.DashboardResponse.Totals;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.ActivityRepository;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.OutreachDraftRepository;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.SceneRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The overview the productions page opens with, across the active productions the person is on. */
@Service
public class DashboardService {

    static final int UPCOMING = 6;
    static final int FRESH_FINDS = 6;
    static final int ACTIVITY = 8;
    private static final List<LocationStatus> IN_PLAY = List.of(LocationStatus.SHORTLISTED, LocationStatus.CONTACTED, LocationStatus.CONFIRMED);

    private final ProjectRepository projects;
    private final SceneRepository scenes;
    private final LocationRepository locations;
    private final OutreachDraftRepository drafts;
    private final ActivityRepository activity;
    private final BlockingTransactions db;

    public DashboardService(ProjectRepository projects, SceneRepository scenes, LocationRepository locations, OutreachDraftRepository drafts,
                            ActivityRepository activity, BlockingTransactions db) {
        this.projects = projects;
        this.scenes = scenes;
        this.locations = locations;
        this.drafts = drafts;
        this.activity = activity;
        this.db = db;
    }

    public Mono<DashboardResponse> dashboard(UUID userId) {
        return db.call(() -> {
            List<UUID> ids = projects.findActiveIdsForMember(userId);
            if (ids.isEmpty()) {
                return new DashboardResponse(new Totals(0, 0, 0, 0, 0), List.of(), List.of(), List.of());
            }
            Totals totals = new Totals(ids.size(), sum(scenes.countByProjects(ids)),
                    sum(locations.countScenesByProjectsAndStatus(ids, LocationStatus.CONFIRMED)),
                    locations.countByProjectsAndStatuses(ids, IN_PLAY), sum(drafts.countDueForFollowUpByProjects(ids)));
            return new DashboardResponse(totals, upcoming(ids), freshFinds(ids), latest(ids));
        });
    }

    private List<ShootDay> upcoming(List<UUID> ids) {
        List<Scene> next = scenes.findUpcoming(ids, LocalDate.ofInstant(DatabaseTime.now(), ZoneOffset.UTC), PageRequest.of(0, UPCOMING));
        if (next.isEmpty()) {
            return List.of();
        }
        Map<UUID, Location> confirmed = locations.findConfirmedByScenes(next.stream().map(Scene::getId).toList()).stream()
                .collect(Collectors.toMap(venue -> venue.getScene().getId(), Function.identity(), (first, second) -> first));
        return next.stream().map(scene -> {
            Location venue = confirmed.get(scene.getId());
            LocalDate date = scene.getShootDateStart() != null ? scene.getShootDateStart() : scene.getShootDateEnd();
            return new ShootDay(scene.getId(), scene.getSceneNumber(), scene.getTitle(), scene.getProject().getId(), scene.getProject().getTitle(),
                    date, scene.getCallTime(), venue == null ? null : venue.getId(), venue == null ? null : venue.getName(),
                    venue == null ? null : venue.getImageUrl());
        }).toList();
    }

    private List<FreshFind> freshFinds(List<UUID> ids) {
        return locations.findFreshFinds(ids, PageRequest.of(0, FRESH_FINDS)).stream()
                .map(venue -> new FreshFind(venue.getId(), venue.getName(), venue.getAddress(),
                        venue.getFitScore() == null ? null : venue.getFitScore().intValue(), venue.getImageUrl(), venue.getStatus(),
                        venue.getScene().getId(), venue.getScene().getTitle(), venue.getScene().getProject().getTitle()))
                .toList();
    }

    private List<Happening> latest(List<UUID> ids) {
        return activity.findLatest(ids, PageRequest.of(0, ACTIVITY)).stream()
                .map(line -> new Happening(ActivityResponse.from(line), line.getProject().getId(), line.getProject().getTitle()))
                .toList();
    }

    private static long sum(List<SceneRepository.ProjectCount> counts) {
        return counts.stream().mapToLong(SceneRepository.ProjectCount::getTotal).sum();
    }
}
