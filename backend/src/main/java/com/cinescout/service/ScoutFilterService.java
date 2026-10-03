package com.cinescout.service;

import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.ScoutFilters;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** A project's default scouting filters: read by anyone on the crew, set by editors and the owner. */
@Service
public class ScoutFilterService {

    private final ProjectRepository projects;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public ScoutFilterService(ProjectRepository projects, ProjectAccess access, BlockingTransactions db) {
        this.projects = projects;
        this.access = access;
        this.db = db;
    }

    /** The project's filters; all empty while none are set. */
    public Mono<ScoutFilters> get(UUID userId, UUID projectId) {
        return db.call(() -> {
            ScoutFilters filters = access.project(userId, projectId, ProjectRole.VIEWER).getScoutFilters();
            return filters == null ? ScoutFilters.NONE : filters;
        });
    }

    /** Replaces them as a whole. */
    public Mono<ScoutFilters> set(UUID userId, UUID projectId, ScoutFilters filters) {
        return db.call(() -> {
            Project project = access.project(userId, projectId, ProjectRole.EDITOR);
            project.setScoutFilters(filters);
            return projects.saveAndFlush(project).getScoutFilters();
        });
    }
}
