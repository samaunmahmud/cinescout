package com.cinescout.service;

import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectStatus;
import com.cinescout.dto.CreateProjectRequest;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.ProjectProgressResponse;
import com.cinescout.dto.ProjectResponse;
import com.cinescout.dto.UpdateProjectRequest;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.SceneRepository;
import com.cinescout.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** A user's projects. Every operation is scoped to {@code ownerId}; entities never leave this class. */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final UserRepository users;
    private final SceneRepository scenes;
    private final LocationRepository locations;
    private final BlockingTransactions db;

    public ProjectService(ProjectRepository projects, UserRepository users, SceneRepository scenes, LocationRepository locations,
                          BlockingTransactions db) {
        this.projects = projects;
        this.users = users;
        this.scenes = scenes;
        this.locations = locations;
        this.db = db;
    }

    public Mono<ProjectResponse> create(UUID ownerId, CreateProjectRequest request) {
        return db.call(() -> {
            Project project = new Project(users.getReferenceById(ownerId), request.title().strip(), blankToNull(request.description()));
            project.setLocationArea(request.locationArea());
            return ProjectResponse.from(projects.saveAndFlush(project));
        });
    }

    /** Newest first; {@code status} null means all. */
    public Mono<PageResponse<ProjectResponse>> list(UUID ownerId, ProjectStatus status, PageQuery page) {
        return db.call(() -> PageResponse.from(status == null
                ? projects.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId, page.pageable())
                : projects.findByOwnerIdAndStatusOrderByCreatedAtDescIdDesc(ownerId, status, page.pageable()),
                ProjectResponse::from));
    }

    public Mono<ProjectResponse> get(UUID ownerId, UUID projectId) {
        return db.call(() -> ProjectResponse.from(owned(ownerId, projectId)));
    }

    /** Full replacement: a null description or location area clears it. */
    public Mono<ProjectResponse> update(UUID ownerId, UUID projectId, UpdateProjectRequest request) {
        return db.call(() -> {
            Project project = owned(ownerId, projectId);
            project.setTitle(request.title().strip());
            project.setDescription(blankToNull(request.description()));
            project.setLocationArea(request.locationArea());
            project.setStatus(request.status());
            return ProjectResponse.from(projects.saveAndFlush(project));
        });
    }

    /** How far the project's scouting has come: its scenes, and its candidate locations by status. */
    public Mono<ProjectProgressResponse> progress(UUID ownerId, UUID projectId) {
        return db.call(() -> {
            owned(ownerId, projectId);
            Map<LocationStatus, Long> byStatus = new EnumMap<>(LocationStatus.class);
            for (LocationStatus status : LocationStatus.values()) {
                byStatus.put(status, 0L);
            }
            long confirmedScenes = 0;
            for (LocationRepository.StatusCount count : locations.countByStatus(projectId)) {
                byStatus.put(count.getStatus(), count.getLocations());
                if (count.getStatus() == LocationStatus.CONFIRMED) {
                    confirmedScenes = count.getScenes();
                }
            }
            return new ProjectProgressResponse(scenes.countByProjectId(projectId), locations.countScenesWithLocations(projectId),
                    confirmedScenes, byStatus.values().stream().mapToLong(Long::longValue).sum(), byStatus);
        });
    }

    /** Deletes the project; the database cascades to its scenes, locations and drafts. */
    public Mono<Void> delete(UUID ownerId, UUID projectId) {
        return db.run(() -> projects.delete(owned(ownerId, projectId)));
    }

    private Project owned(UUID ownerId, UUID projectId) {
        return projects.findByIdAndOwnerId(projectId, ownerId).orElseThrow(() -> new NotFoundException("Project", projectId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
