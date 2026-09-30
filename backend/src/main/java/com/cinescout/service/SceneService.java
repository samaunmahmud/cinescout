package com.cinescout.service;

import com.cinescout.domain.Project;
import com.cinescout.domain.Scene;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.SceneRequest;
import com.cinescout.dto.SceneResponse;
import com.cinescout.dto.ShootDatesRequest;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.SceneRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.UUID;

/**
 * The scenes of a user's projects. Requirements extraction is separate ({@code SceneScoutingService});
 * here a scene is just its script, number and shoot window.
 */
@Service
public class SceneService {

    private final SceneRepository scenes;
    private final ProjectRepository projects;
    private final BlockingTransactions db;

    public SceneService(SceneRepository scenes, ProjectRepository projects, BlockingTransactions db) {
        this.scenes = scenes;
        this.projects = projects;
        this.db = db;
    }

    /** @throws ConflictException (as an error signal) if the project already has a scene with that number */
    public Mono<SceneResponse> create(UUID ownerId, UUID projectId, SceneRequest request) {
        return db.call(() -> {
                    Project project = projects.findByIdAndOwnerId(projectId, ownerId)
                            .orElseThrow(() -> new NotFoundException("Project", projectId));
                    Scene scene = new Scene(project, request.title().strip(), request.sourceText().strip());
                    apply(scene, request);
                    return SceneResponse.from(scenes.saveAndFlush(scene));
                })
                .onErrorMap(DataIntegrityViolationException.class, Conflicts::translate);
    }

    /**
     * In script order: numbered scenes by number, then unnumbered ones by creation. With {@code search}, only
     * the scenes whose title, script or extracted setting contains it, ignoring case; blank means all.
     */
    public Mono<PageResponse<SceneResponse>> list(UUID ownerId, UUID projectId, String search, PageQuery page) {
        return db.call(() -> {
            projects.findByIdAndOwnerId(projectId, ownerId).orElseThrow(() -> new NotFoundException("Project", projectId));
            return PageResponse.from(search == null || search.isBlank()
                    ? scenes.findOwnedByProject(projectId, ownerId, page.pageable())
                    : scenes.searchOwnedByProject(projectId, ownerId, containing(search), page.pageable()),
                    SceneResponse::from);
        });
    }

    /** A LIKE pattern matching text that contains {@code search} literally: its own % and _ are not wildcards. */
    static String containing(String search) {
        String literal = search.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + literal + "%";
    }

    public Mono<SceneResponse> get(UUID ownerId, UUID sceneId) {
        return db.call(() -> SceneResponse.from(owned(ownerId, sceneId)));
    }

    /**
     * Full replacement of the editable fields. If the script changed, the extracted requirements no
     * longer describe it, so they are dropped and the scene goes back to needing a parse.
     */
    public Mono<SceneResponse> update(UUID ownerId, UUID sceneId, SceneRequest request) {
        return db.call(() -> {
                    Scene scene = owned(ownerId, sceneId);
                    if (!scene.getSourceText().equals(request.sourceText().strip())) {
                        scene.resetRequirements();
                    }
                    scene.setTitle(request.title().strip());
                    scene.setSourceText(request.sourceText().strip());
                    apply(scene, request);
                    return SceneResponse.from(scenes.saveAndFlush(scene));
                })
                .onErrorMap(DataIntegrityViolationException.class, Conflicts::translate);
    }

    /** Sets when the scene is shot and nothing else: the script and its requirements stay as they are. */
    public Mono<SceneResponse> reschedule(UUID ownerId, UUID sceneId, ShootDatesRequest request) {
        return db.call(() -> {
            Scene scene = owned(ownerId, sceneId);
            scene.setShootDateStart(request.shootDateStart());
            scene.setShootDateEnd(request.shootDateEnd());
            return SceneResponse.from(scenes.saveAndFlush(scene));
        });
    }

    public Mono<Void> delete(UUID ownerId, UUID sceneId) {
        return db.run(() -> scenes.delete(owned(ownerId, sceneId)));
    }

    private static void apply(Scene scene, SceneRequest request) {
        scene.setSceneNumber(request.sceneNumber());
        scene.setShootDateStart(request.shootDateStart());
        scene.setShootDateEnd(request.shootDateEnd());
    }

    private Scene owned(UUID ownerId, UUID sceneId) {
        return scenes.findOwned(sceneId, ownerId).orElseThrow(() -> new NotFoundException("Scene", sceneId));
    }
}
