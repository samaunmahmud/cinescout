package com.cinescout.service;

import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectRole;
import com.cinescout.dto.ProjectSettings;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** A project's working settings: read by anyone on the crew, set by editors and the owner. */
@Service
public class ProjectSettingsService {

    private final ProjectRepository projects;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public ProjectSettingsService(ProjectRepository projects, ProjectAccess access, BlockingTransactions db) {
        this.projects = projects;
        this.access = access;
        this.db = db;
    }

    public Mono<ProjectSettings> get(UUID userId, UUID projectId) {
        return db.call(() -> ProjectSettings.from(access.project(userId, projectId, ProjectRole.VIEWER)));
    }

    /**
     * Replaces them, except that a weather threshold left out (null) keeps its value. A new follow-up period counts from
     * the next run of the follow-up job; emails already flagged stay flagged. New thresholds apply from the next weather watch.
     */
    public Mono<ProjectSettings> set(UUID userId, UUID projectId, ProjectSettings settings) {
        return db.call(() -> {
            Project project = access.project(userId, projectId, ProjectRole.EDITOR);
            project.setFollowUpDays(settings.followUpDays());
            if (settings.rainAlertPercent() != null) {
                project.setRainAlertPercent(settings.rainAlertPercent());
            }
            if (settings.windAlertKmh() != null) {
                project.setWindAlertKmh(settings.windAlertKmh());
            }
            return ProjectSettings.from(projects.saveAndFlush(project));
        });
    }
}
