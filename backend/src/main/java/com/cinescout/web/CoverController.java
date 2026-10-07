package com.cinescout.web;

import com.cinescout.dto.CoverRequest;
import com.cinescout.dto.CoverResponse;
import com.cinescout.dto.CoverTriggerRequest;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.CoverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
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

import java.util.UUID;

@RestController
@Tag(name = "Scenes", description = "A scene's script, number and shoot window. Its filming requirements are extracted by POST /api/scenes/{sceneId}/parse.")
class CoverController {

    private final CoverService covers;

    CoverController(CoverService covers) {
        this.covers = covers;
    }

    @Operation(summary = "List a scene's cover sets, in the order they were added")
    @GetMapping("/api/scenes/{sceneId}/covers")
    Mono<PageResponse<CoverResponse>> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                                           @Valid @ParameterObject PageQuery page) {
        return covers.list(user.id(), sceneId, page);
    }

    @Operation(summary = "Make one of the scene's candidates a cover set",
            description = "The venue must be a candidate of this scene (add a library venue to the scene first) and not the one confirmed "
                    + "for it. The trigger says when to switch, e.g. \"if rain > 60%\". At most 5 a scene.")
    @PostMapping("/api/scenes/{sceneId}/covers")
    @ResponseStatus(HttpStatus.CREATED)
    Mono<CoverResponse> add(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                            @Valid @RequestBody CoverRequest request) {
        return covers.add(user.id(), sceneId, request);
    }

    @Operation(summary = "Change when to switch to a cover set")
    @PutMapping("/api/covers/{coverId}")
    Mono<CoverResponse> setTrigger(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID coverId,
                                   @Valid @RequestBody CoverTriggerRequest request) {
        return covers.setTrigger(user.id(), coverId, request);
    }

    @Operation(summary = "Stop using a venue as a cover set", description = "The venue stays a candidate of the scene.")
    @DeleteMapping("/api/covers/{coverId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID coverId) {
        return covers.remove(user.id(), coverId);
    }
}
