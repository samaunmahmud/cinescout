package com.cinescout.web;

import com.cinescout.dto.ProjectSettings;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.ProjectSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Tag(name = "Projects", description = "A film or production. A project's location area is where its scenes are scouted.")
class ProjectSettingsController {

    private final ProjectSettingsService settings;

    ProjectSettingsController(ProjectSettingsService settings) {
        this.settings = settings;
    }

    @Operation(summary = "Get a project's settings")
    @GetMapping("/api/projects/{projectId}/settings")
    Mono<ProjectSettings> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return settings.get(user.id(), projectId);
    }

    @Operation(summary = "Set a project's settings",
            description = "A full replacement. followUpDays (1 to 60, default 5) is how long a sent outreach email may go unanswered "
                    + "before it is flagged for a follow-up.")
    @PutMapping("/api/projects/{projectId}/settings")
    Mono<ProjectSettings> set(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                              @Valid @RequestBody ProjectSettings request) {
        return settings.set(user.id(), projectId, request);
    }
}
