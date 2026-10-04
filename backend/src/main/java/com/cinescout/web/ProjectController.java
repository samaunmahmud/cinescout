package com.cinescout.web;

import com.cinescout.domain.ProjectStatus;
import com.cinescout.dto.CreateProjectRequest;
import com.cinescout.dto.MovesResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.ProjectProgressResponse;
import com.cinescout.dto.ProjectResponse;
import com.cinescout.dto.ScheduleResponse;
import com.cinescout.dto.UpdateProjectRequest;
import com.cinescout.ratelimit.RateLimit;
import com.cinescout.ratelimit.RateLimiter;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.MovesService;
import com.cinescout.service.ProjectService;
import com.cinescout.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "A film or production. A project's location area is where its scenes are scouted.")
class ProjectController {

    private final ProjectService projects;
    private final ScheduleService schedules;
    private final MovesService moves;
    private final RateLimiter limits;

    ProjectController(ProjectService projects, ScheduleService schedules, MovesService moves, RateLimiter limits) {
        this.projects = projects;
        this.schedules = schedules;
        this.moves = moves;
        this.limits = limits;
    }

    @Operation(summary = "Create a project")
    @ApiResponse(responseCode = "201", description = "Created; the Location header points at the new project")
    @PostMapping
    Mono<ResponseEntity<ProjectResponse>> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @Valid @RequestBody CreateProjectRequest request) {
        return projects.create(user.id(), request)
                .map(project -> ResponseEntity.created(URI.create("/api/projects/" + project.id())).body(project));
    }

    /** The caller's projects, newest first, a page at a time; {@code status} narrows the list. */
    @Operation(summary = "List the caller's projects, newest first")
    @GetMapping
    Mono<PageResponse<ProjectResponse>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestParam(required = false) ProjectStatus status, @Valid @ParameterObject PageQuery page) {
        return projects.list(user.id(), status, page);
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

    /** A number for each stage of the project's scouting, for a progress display. */
    @Operation(summary = "Get a project's scouting progress",
            description = "How many scenes the project has, how many have candidate locations and a confirmed one, and the candidate locations by status.")
    @GetMapping("/{projectId}/progress")
    Mono<ProjectProgressResponse> progress(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return projects.progress(user.id(), projectId);
    }

    /** The project's scenes by shoot day, each with its confirmed venue. */
    @Operation(summary = "Get a project's company moves",
            description = "For each shoot day with more than one confirmed venue, the drive from each to the next in the day's order "
                    + "(call time, then script order), by OSRM without traffic. New pairs of places are looked up (a few a request, counted "
                    + "as lookups) and kept; the rest show as PENDING until the next request. Moves longer than warnAfterMinutes are flagged.")
    @GetMapping("/{projectId}/moves")
    Mono<MovesResponse> moves(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return moves.moves(user.id(), projectId, () -> limits.acquire(RateLimit.LOOKUPS, user.id()));
    }

    @Operation(summary = "Get a project's shoot schedule",
            description = "The scenes grouped by the day their shoot starts, earliest first, each with its confirmed locations and how many "
                    + "candidates it has; scenes without shoot dates are listed separately. Shows what still needs a date or a venue.")
    @GetMapping("/{projectId}/schedule")
    Mono<ScheduleResponse> schedule(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return schedules.schedule(user.id(), projectId);
    }

    /** Deletes the project with all its scenes, locations and drafts. */
    @Operation(summary = "Delete a project", description = "Also deletes its scenes, locations and outreach drafts.")
    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return projects.delete(user.id(), projectId);
    }
}
