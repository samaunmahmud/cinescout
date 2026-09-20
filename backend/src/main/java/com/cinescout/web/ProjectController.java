package com.cinescout.web;

import com.cinescout.domain.ProjectStatus;
import com.cinescout.dto.CreateProjectRequest;
import com.cinescout.dto.ProjectResponse;
import com.cinescout.dto.UpdateProjectRequest;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "A film or production. A project's location area is where its scenes are scouted.")
class ProjectController {

    private final ProjectService projects;

    ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @Operation(summary = "Create a project")
    @ApiResponse(responseCode = "201", description = "Created; the Location header points at the new project")
    @PostMapping
    Mono<ResponseEntity<ProjectResponse>> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @Valid @RequestBody CreateProjectRequest request) {
        return projects.create(user.id(), request)
                .map(project -> ResponseEntity.created(URI.create("/api/projects/" + project.id())).body(project));
    }

    /** The caller's projects, newest first; {@code status} narrows the list. */
    @Operation(summary = "List the caller's projects")
    @GetMapping
    Mono<List<ProjectResponse>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                     @RequestParam(required = false) ProjectStatus status) {
        return projects.list(user.id(), status);
    }

    @Operation(summary = "Get a project")
    @GetMapping("/{projectId}")
    Mono<ProjectResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return projects.get(user.id(), projectId);
    }

    /** Full replacement: a missing description or location area clears it. */
    @Operation(summary = "Replace a project", description = "Full replacement: an omitted description or location area is cleared.")
    @PutMapping("/{projectId}")
    Mono<ProjectResponse> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                 @Valid @RequestBody UpdateProjectRequest request) {
        return projects.update(user.id(), projectId, request);
    }

    /** Deletes the project with all its scenes, locations and drafts. */
    @Operation(summary = "Delete a project", description = "Also deletes its scenes, locations and outreach drafts.")
    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return projects.delete(user.id(), projectId);
    }
}
