package com.cinescout.web;

import com.cinescout.dto.ImportScriptRequest;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.SceneRequest;
import com.cinescout.dto.SceneResponse;
import com.cinescout.dto.ShootDatesRequest;
import com.cinescout.dto.ScriptImportResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.SceneService;
import com.cinescout.service.ScriptImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api")
@Tag(name = "Scenes", description = "A scene's script, number and shoot window. Its filming requirements are extracted by POST /api/scenes/{sceneId}/parse.")
class SceneController {

    private final SceneService scenes;
    private final ScriptImportService scripts;

    SceneController(SceneService scenes, ScriptImportService scripts) {
        this.scenes = scenes;
        this.scripts = scripts;
    }

    @Operation(summary = "Add a scene to a project", description = "A scene number, when given, must be unique within the project (409 otherwise).")
    @ApiResponse(responseCode = "201", description = "Created; the Location header points at the new scene")
    @PostMapping("/projects/{projectId}/scenes")
    Mono<ResponseEntity<SceneResponse>> create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                               @Valid @RequestBody SceneRequest request) {
        return scenes.create(user.id(), projectId, request)
                .map(scene -> ResponseEntity.created(URI.create("/api/scenes/" + scene.id())).body(scene));
    }

    /** Shows what {@link #importScript} would add, so the user can check the cut before committing to it. */
    @Operation(summary = "Preview the scenes a script would be cut into",
            description = "Finds the scene headings (INT./EXT. lines) in a pasted screenplay and returns the scenes an import would add. Nothing is saved.")
    @PostMapping("/projects/{projectId}/scenes/import/preview")
    Mono<ScriptImportResponse> previewImport(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                             @Valid @RequestBody ImportScriptRequest request) {
        return scripts.preview(user.id(), projectId, request.script());
    }

    @Operation(summary = "Add a whole script as scenes",
            description = """
                    Cuts a pasted screenplay at its scene headings (INT./EXT. lines) and adds one scene per heading, all or none. \
                    The script's own scene numbers are kept when every scene has one and none is taken; otherwise the scenes \
                    are numbered on from the project's last scene. 400 when the script has no scene headings.""")
    @ApiResponse(responseCode = "201", description = "The scenes added, in script order")
    @PostMapping("/projects/{projectId}/scenes/import")
    @ResponseStatus(HttpStatus.CREATED)
    Mono<ScriptImportResponse> importScript(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                            @Valid @RequestBody ImportScriptRequest request) {
        return scripts.importScript(user.id(), projectId, request.script());
    }

    /** The project's scenes in script order: numbered ones by number, then unnumbered ones. */
    @Operation(summary = "List a project's scenes in script order",
            description = "`q` narrows the list to the scenes whose title, script or extracted setting contains it, ignoring case.")
    @GetMapping("/projects/{projectId}/scenes")
    Mono<PageResponse<SceneResponse>> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                           @RequestParam(required = false) @Size(max = 100) String q,
                                           @Valid @ParameterObject PageQuery page) {
        return scenes.list(user.id(), projectId, q, page);
    }

    @Operation(summary = "Get a scene")
    @GetMapping("/scenes/{sceneId}")
    Mono<SceneResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId) {
        return scenes.get(user.id(), sceneId);
    }

    /** Full replacement. Changing the script discards the requirements extracted from the old one. */
    @Operation(summary = "Replace a scene", description = "Full replacement. Changing the script discards the requirements extracted from the old one.")
    @PutMapping("/scenes/{sceneId}")
    Mono<SceneResponse> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                               @Valid @RequestBody SceneRequest request) {
        return scenes.update(user.id(), sceneId, request);
    }

    /** For scheduling from a list of scenes, where the script is not at hand to send back with a full replacement. */
    @Operation(summary = "Set a scene's shoot dates",
            description = "Replaces the shoot window and nothing else: an omitted date is cleared. The last day must not be before the first (400).")
    @PutMapping("/scenes/{sceneId}/shoot-dates")
    Mono<SceneResponse> reschedule(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId,
                                   @Valid @RequestBody ShootDatesRequest request) {
        return scenes.reschedule(user.id(), sceneId, request);
    }

    @Operation(summary = "Delete a scene", description = "Also deletes its locations.")
    @DeleteMapping("/scenes/{sceneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sceneId) {
        return scenes.delete(user.id(), sceneId);
    }
}
