package com.cinescout.service;

import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectMember;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.User;
import com.cinescout.domain.ProjectStatus;
import com.cinescout.dto.CreateProjectRequest;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.ProjectProgressResponse;
import com.cinescout.dto.ProjectResponse;
import com.cinescout.dto.UpdateProjectRequest;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.ProjectMemberRepository;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.SceneRepository;
import com.cinescout.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The projects a user is on. Every operation goes through {@link ProjectAccess}: reading needs any role, changing
 * the details EDITOR, archiving and deleting OWNER. Entities never leave this class.
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final UserRepository users;
    private final SceneRepository scenes;
    private final LocationRepository locations;
    private final ProjectMemberRepository members;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public ProjectService(ProjectRepository projects, UserRepository users, SceneRepository scenes, LocationRepository locations,
                          ProjectMemberRepository members, ProjectAccess access, BlockingTransactions db) {
        this.projects = projects;
        this.users = users;
        this.scenes = scenes;
        this.locations = locations;
        this.members = members;
        this.access = access;
        this.db = db;
    }

    /** The creator becomes the project's owner. */
    public Mono<ProjectResponse> create(UUID userId, CreateProjectRequest request) {
        return db.call(() -> {
            User creator = users.getReferenceById(userId);
            Project project = new Project(creator, request.title().strip(), blankToNull(request.description()));
            project.setLocationArea(request.locationArea());
            Project saved = projects.saveAndFlush(project);
            members.saveAndFlush(new ProjectMember(saved, creator, ProjectRole.OWNER, null));
            return ProjectResponse.from(saved, 0, 0, null, ProjectRole.OWNER);
        });
    }

    /** Newest first; {@code status} null means all. */
    public Mono<PageResponse<ProjectResponse>> list(UUID userId, ProjectStatus status, PageQuery query) {
        return db.call(() -> {
            Page<Project> page = status == null
                    ? projects.findVisible(userId, query.pageable())
                    : projects.findVisibleByStatus(userId, status, query.pageable());
            Counts counts = counts(userId, page.getContent().stream().map(Project::getId).toList());
            return PageResponse.from(page, counts::respond);
        });
    }

    public Mono<ProjectResponse> get(UUID userId, UUID projectId) {
        return db.call(() -> respond(userId, access.project(userId, projectId, ProjectRole.VIEWER)));
    }

    /**
     * Full replacement: a null description or location area clears it. Editors may change the details; archiving or
     * restoring the project is the owner's call.
     */
    public Mono<ProjectResponse> update(UUID userId, UUID projectId, UpdateProjectRequest request) {
        return db.call(() -> {
            Project current = access.project(userId, projectId, ProjectRole.EDITOR);
            Project project = request.status() == current.getStatus() ? current : access.project(userId, projectId, ProjectRole.OWNER);
            project.setTitle(request.title().strip());
            project.setDescription(blankToNull(request.description()));
            project.setLocationArea(request.locationArea());
            project.setStatus(request.status());
            return respond(userId, projects.saveAndFlush(project));
        });
    }

    /** How far the project's scouting has come: its scenes, and its candidate locations by status. */
    public Mono<ProjectProgressResponse> progress(UUID userId, UUID projectId) {
        return db.call(() -> {
            access.project(userId, projectId, ProjectRole.VIEWER);
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

    /** Deletes the project, the owner's call; the database cascades to its scenes, locations, drafts and crew. */
    public Mono<Void> delete(UUID userId, UUID projectId) {
        return db.run(() -> projects.delete(access.project(userId, projectId, ProjectRole.OWNER)));
    }

    private ProjectResponse respond(UUID userId, Project project) {
        return counts(userId, List.of(project.getId())).respond(project);
    }

    /**
     * The scene counts, poster pictures and the user's roles for a page of projects, in four queries however many
     * projects there are.
     */
    private Counts counts(UUID userId, List<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return new Counts(Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<UUID, String> images = new HashMap<>();
        locations.findImagesByProjects(projectIds).forEach(image -> images.putIfAbsent(image.getProjectId(), image.getImageUrl()));
        Map<UUID, ProjectRole> roles = members.findRoles(userId, projectIds).stream()
                .collect(Collectors.toMap(ProjectMemberRepository.ProjectRoleRow::getProjectId, ProjectMemberRepository.ProjectRoleRow::getRole));
        return new Counts(byProject(scenes.countByProjects(projectIds)),
                byProject(locations.countScenesByProjectsAndStatus(projectIds, LocationStatus.CONFIRMED)), images, roles);
    }

    private static Map<UUID, Long> byProject(List<SceneRepository.ProjectCount> counts) {
        return counts.stream().collect(Collectors.toMap(SceneRepository.ProjectCount::getProjectId, SceneRepository.ProjectCount::getTotal));
    }

    private record Counts(Map<UUID, Long> scenes, Map<UUID, Long> confirmed, Map<UUID, String> images, Map<UUID, ProjectRole> roles) {
        ProjectResponse respond(Project project) {
            return ProjectResponse.from(project, scenes.getOrDefault(project.getId(), 0L), confirmed.getOrDefault(project.getId(), 0L),
                    images.get(project.getId()), roles.get(project.getId()));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
