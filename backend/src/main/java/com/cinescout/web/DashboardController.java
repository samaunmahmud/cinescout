package com.cinescout.web;

import com.cinescout.dto.DashboardResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Tag(name = "Projects", description = "A user's projects.")
class DashboardController {

    private final DashboardService dashboard;

    DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @Operation(summary = "The overview across your active productions",
            description = "Headline numbers, the next shoot days (with each scene's confirmed venue), the best-scoring venues nobody has "
                    + "looked at yet, and the latest activity, across the active productions you are on.")
    @GetMapping("/api/dashboard")
    Mono<DashboardResponse> dashboard(@AuthenticationPrincipal AuthenticatedUser user) {
        return dashboard.dashboard(user.id());
    }
}
