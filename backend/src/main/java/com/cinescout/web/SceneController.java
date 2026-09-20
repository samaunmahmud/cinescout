package com.cinescout.web;

import com.cinescout.dto.SceneRequest;
import com.cinescout.dto.SceneResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.SceneService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
class SceneController {

    private final SceneService scenes;

    SceneController(SceneService scenes) {
        this.scenes = scenes;
    }

    @PostMapping("/projects/{projectId}/scenes")
    Mono<ResponseEntity<SceneResponse>> create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                               @Valid @RequestBody SceneRequest request) {
        return scenes.create(user.id(), projectId, request)
                .map(scene -> ResponseEntity.created(URI.create("/api/scenes/" + scene.id())).body(scene));
    }

    /** The project's scenes in script order: numbered ones by number, then unnumbered ones. */
    @GetMapping("/projects/{projectId}/scenes")
    Mono<List<SceneResponse>> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return scenes.list(user.id(), projectId);
    }

    @GetMapping("/scenes/{sceneId}")
    Mono<SceneResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId) {
        return scenes.get(user.id(), sceneId);
    }

    /** Full replacement. Changing the script discards the requirements extracted from the old one. */
    @PutMapping("/scenes/{sceneId}")
    Mono<SceneResponse> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                               @Valid @RequestBody SceneRequest request) {
        return scenes.update(user.id(), sceneId, request);
    }

    @DeleteMapping("/scenes/{sceneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId) {
        return scenes.delete(user.id(), sceneId);
    }
}
