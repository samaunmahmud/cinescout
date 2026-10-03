package com.cinescout.service;

import com.cinescout.domain.Location;
import com.cinescout.domain.OutreachDraft;
import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.OutreachDraftRepository;
import com.cinescout.repository.ProjectMemberRepository;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.SceneRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The one door to a project and everything in it. A user who is not on the project gets {@link NotFoundException},
 * so a project they are not on looks exactly like one that does not exist; a member whose role is too weak for what
 * they ask gets {@link ForbiddenException}. Call inside a transaction, as the entities come back attached.
 */
@Component
public class ProjectAccess {

    private final ProjectRepository projects;
    private final SceneRepository scenes;
    private final LocationRepository locations;
    private final OutreachDraftRepository drafts;
    private final ProjectMemberRepository members;

    public ProjectAccess(ProjectRepository projects, SceneRepository scenes, LocationRepository locations,
                         OutreachDraftRepository drafts, ProjectMemberRepository members) {
        this.projects = projects;
        this.scenes = scenes;
        this.locations = locations;
        this.drafts = drafts;
        this.members = members;
    }

    public Project project(UUID userId, UUID projectId, ProjectRole needed) {
        Project project = projects.findVisible(projectId, userId).orElseThrow(() -> new NotFoundException("Project", projectId));
        require(userId, projectId, needed);
        return project;
    }

    public Scene scene(UUID userId, UUID sceneId, ProjectRole needed) {
        Scene scene = scenes.findVisible(sceneId, userId).orElseThrow(() -> new NotFoundException("Scene", sceneId));
        require(userId, scene.getProject().getId(), needed);
        return scene;
    }

    public Location location(UUID userId, UUID locationId, ProjectRole needed) {
        Location location = locations.findVisible(locationId, userId).orElseThrow(() -> new NotFoundException("Location", locationId));
        require(userId, location.getScene().getProject().getId(), needed);
        return location;
    }

    public OutreachDraft draft(UUID userId, UUID draftId, ProjectRole needed) {
        OutreachDraft draft = drafts.findVisible(draftId, userId).orElseThrow(() -> new NotFoundException("Outreach draft", draftId));
        require(userId, draft.getLocation().getScene().getProject().getId(), needed);
        return draft;
    }

    /** The user's role on the project. */
    public ProjectRole role(UUID userId, UUID projectId) {
        return members.findRole(projectId, userId).orElseThrow(() -> new NotFoundException("Project", projectId));
    }

    private void require(UUID userId, UUID projectId, ProjectRole needed) {
        if (!role(userId, projectId).atLeast(needed)) {
            throw new ForbiddenException(needed == ProjectRole.OWNER
                    ? "Only the project's owner can do this"
                    : "Viewers can look at this project but not change it");
        }
    }
}
