package com.cinescout.web;

import com.cinescout.dto.ShotMoveRequest;
import com.cinescout.dto.ShotRequest;
import com.cinescout.dto.ShotResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.ShotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Shot list", description = "A scene's shots in shooting order, each with where the sun will be when it is shot.")
class ShotController {

    private final ShotService shots;

    ShotController(ShotService shots) {
        this.shots = shots;
    }

    @Operation(summary = "List a scene's shots",
            description = "In shooting order, numbered from 1. Each says how the sun lights it at its planned time on the scene's first "
                    + "shoot day at its venue (the scene's confirmed venue unless it names another), or what is missing to tell.")
    @GetMapping("/api/scenes/{sceneId}/shots")
    Mono<List<ShotResponse>> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId) {
        return shots.list(user.id(), sceneId);
    }

    @Operation(summary = "Add a shot at the end of the list", description = "Answers with the whole list. At most 150 a scene.")
    @PostMapping("/api/scenes/{sceneId}/shots")
    @ResponseStatus(HttpStatus.CREATED)
    Mono<List<ShotResponse>> add(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                                 @Valid @RequestBody ShotRequest request) {
        return shots.add(user.id(), sceneId, request);
    }

    @Operation(summary = "Change a shot", description = "Answers with the whole list.")
    @PutMapping("/api/shots/{shotId}")
    Mono<List<ShotResponse>> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID shotId,
                                    @Valid @RequestBody ShotRequest request) {
        return shots.update(user.id(), shotId, request);
    }

    @Operation(summary = "Move a shot one place earlier or later", description = "Answers with the whole list.")
    @PutMapping("/api/shots/{shotId}/position")
    Mono<List<ShotResponse>> move(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID shotId,
                                  @Valid @RequestBody ShotMoveRequest request) {
        return shots.move(user.id(), shotId, request.direction());
    }

    @Operation(summary = "Remove a shot", description = "Answers with the whole list.")
    @DeleteMapping("/api/shots/{shotId}")
    Mono<List<ShotResponse>> remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID shotId) {
        return shots.remove(user.id(), shotId);
    }
}
