package com.cinescout.web;

import com.cinescout.domain.ActivityKind;
import com.cinescout.dto.ActivityResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Tag(name = "Activity", description = "What happened on a project, and who did it.")
class ActivityController {

    private final ActivityService activity;

    ActivityController(ActivityService activity) {
        this.activity = activity;
    }

    @Operation(summary = "Read a project's activity log",
            description = "Newest first. `kind` keeps one kind: VENUE, SCOUTING, OUTREACH, CREW, SCHEDULE or COMMENT.")
    @GetMapping("/api/projects/{projectId}/activity")
    Mono<PageResponse<ActivityResponse>> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                              @RequestParam(required = false) ActivityKind kind, @Valid @ParameterObject PageQuery page) {
        return activity.list(user.id(), projectId, kind, page);
    }
}
