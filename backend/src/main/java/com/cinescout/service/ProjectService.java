package com.cinescout.service;

import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectStatus;
import com.cinescout.dto.CreateProjectRequest;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.ProjectResponse;
import com.cinescout.dto.UpdateProjectRequest;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** A user's projects. Every operation is scoped to {@code ownerId}; entities never leave this class. */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final UserRepository users;
    private final BlockingTransactions db;

    public ProjectService(ProjectRepository projects, UserRepository users, BlockingTransactions db) {
        this.projects = projects;
        this.users = users;
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
