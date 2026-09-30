package com.cinescout.service;

import com.cinescout.domain.Project;
import com.cinescout.domain.Scene;
import com.cinescout.dto.ScriptImportResponse;
import com.cinescout.dto.ScriptImportResponse.ImportedScene;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.SceneRepository;
import com.cinescout.script.ScriptScene;
import com.cinescout.script.ScriptSplitter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Adds a whole script to a project as scenes, one per scene heading. The scenes start unparsed, like ones
 * typed in one at a time: nothing here calls the AI.
 */
@Service
public class ScriptImportService {

    /** More headings than this is not a screenplay (a feature has 100 to 200 scenes). */
    public static final int MAX_SCENES = 500;

    /** The limits of a scene, as {@code SceneRequest} sets them for one typed in by hand. */
    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_TEXT_LENGTH = 20_000;

    private final SceneRepository scenes;
    private final ProjectRepository projects;
    private final BlockingTransactions db;

    public ScriptImportService(SceneRepository scenes, ProjectRepository projects, BlockingTransactions db) {
        this.scenes = scenes;
        this.projects = projects;
        this.db = db;
    }

    /** The scenes an import of {@code script} would add, without adding them. */
    public Mono<ScriptImportResponse> preview(UUID ownerId, UUID projectId, String script) {
        return db.call(() -> {
            owned(ownerId, projectId);
            Plan plan = plan(projectId, script);
            List<ImportedScene> found = new ArrayList<>();
            for (int i = 0; i < plan.scenes().size(); i++) {
                found.add(describe(null, plan.numbers().get(i), plan.scenes().get(i)));
            }
            return new ScriptImportResponse(found, plan.scriptNumbersKept());
        });
    }

    /**
     * Adds the script's scenes to the project, all or none.
     *
     * @throws InvalidRequestException (as an error signal) if the script has no scene headings, or too many
     */
    public Mono<ScriptImportResponse> importScript(UUID ownerId, UUID projectId, String script) {
        return db.call(() -> {
                    Project project = owned(ownerId, projectId);
                    Plan plan = plan(projectId, script);
                    if (plan.scenes().isEmpty()) {
                        throw new InvalidRequestException("script",
                                "No scene headings found. A scene starts at a line such as \"INT. DINER - NIGHT\"");
                    }
                    List<ImportedScene> added = new ArrayList<>();
                    for (int i = 0; i < plan.scenes().size(); i++) {
                        ScriptScene found = plan.scenes().get(i);
                        Scene scene = new Scene(project, title(found), text(found));
                        scene.setSceneNumber(plan.numbers().get(i));
                        added.add(describe(scenes.save(scene).getId(), scene.getSceneNumber(), found));
                    }
                    scenes.flush();
                    return new ScriptImportResponse(added, plan.scriptNumbersKept());
                })
                .onErrorMap(DataIntegrityViolationException.class, Conflicts::translate);
    }

    private Project owned(UUID ownerId, UUID projectId) {
        return projects.findByIdAndOwnerId(projectId, ownerId).orElseThrow(() -> new NotFoundException("Project", projectId));
    }

    private record Plan(List<ScriptScene> scenes, List<Integer> numbers, boolean scriptNumbersKept) {
    }

    /**
     * The script's own numbers are kept when every scene has one, none repeats and none is taken in the
     * project. Otherwise the scenes are numbered on from the project's highest number, in script order:
     * a half-numbered import would sort its unnumbered scenes after all the others.
     */
    private Plan plan(UUID projectId, String script) {
        List<ScriptScene> found = ScriptSplitter.split(script);
        if (found.size() > MAX_SCENES) {
            throw new InvalidRequestException("script",
                    "That is " + found.size() + " scenes; at most " + MAX_SCENES + " can be imported at once");
        }
        Set<Integer> taken = scenes.findSceneNumbersByProjectId(projectId);
        Set<Integer> seen = new HashSet<>();
        boolean keep = !found.isEmpty() && found.stream()
                .allMatch(scene -> scene.number() != null && !taken.contains(scene.number()) && seen.add(scene.number()));
        if (keep) {
            return new Plan(found, found.stream().map(ScriptScene::number).toList(), true);
        }
        int next = taken.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0) + 1;
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            numbers.add(next + i);
        }
        return new Plan(found, numbers, false);
    }

    private static ImportedScene describe(UUID id, Integer number, ScriptScene scene) {
        return new ImportedScene(id, number, title(scene), text(scene).length(), scene.text().length() > MAX_TEXT_LENGTH);
    }

    private static String title(ScriptScene scene) {
        String heading = scene.heading();
        return heading.length() <= MAX_TITLE_LENGTH ? heading : heading.substring(0, MAX_TITLE_LENGTH).strip();
    }

    private static String text(ScriptScene scene) {
        String text = scene.text();
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH).strip();
    }
}
