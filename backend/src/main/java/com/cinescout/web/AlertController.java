package com.cinescout.web;

import com.cinescout.dto.AlertResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Tag(name = "Alerts", description = "The signed-in person's alerts: weather over a project's thresholds on a shoot day, and emails waiting on a follow-up.")
class AlertController {

    private final AlertService alerts;

    AlertController(AlertService alerts) {
        this.alerts = alerts;
    }

    @Operation(summary = "List your alerts, newest first", description = "unread=true keeps the ones not read yet.")
    @GetMapping("/api/alerts")
    Mono<PageResponse<AlertResponse>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                           @RequestParam(defaultValue = "false") boolean unread, @Valid @ParameterObject PageQuery page) {
        return alerts.list(user.id(), unread, page);
    }

    @Operation(summary = "Count your unread alerts")
    @GetMapping("/api/alerts/unread-count")
    Mono<AlertResponse.Count> unread(@AuthenticationPrincipal AuthenticatedUser user) {
        return alerts.unread(user.id());
    }

    @Operation(summary = "Mark an alert read")
    @PostMapping("/api/alerts/{alertId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> markRead(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID alertId) {
        return alerts.markRead(user.id(), alertId);
    }

    @Operation(summary = "Mark all your alerts read")
    @PostMapping("/api/alerts/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        return alerts.markAllRead(user.id());
    }
}
