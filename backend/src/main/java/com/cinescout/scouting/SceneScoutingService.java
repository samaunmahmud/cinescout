package com.cinescout.scouting;

import com.cinescout.ai.LocationAssessment;
import com.cinescout.ai.SearchResult;
import com.cinescout.domain.Location;
import com.cinescout.domain.Scene;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.SceneResponse;
import com.cinescout.llm.LlmException;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.SceneRepository;
import com.cinescout.scouting.ScoutingException.Kind;
import com.cinescout.service.Conflicts;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scouting for a persisted scene: runs the {@link ScoutingPipeline} and stores what it finds.
 *
 * <p>JPA is blocking, so every database step runs on {@code boundedElastic} in its own short
 * transaction, never on an event-loop thread and never spanning an LLM or search call (those take
 * seconds). Every step re-checks that the scene belongs to {@code ownerId}, so a scene deleted or
 * reassigned mid-run fails cleanly instead of being written to.
 *
 * <p>Two runs racing on the same scene can both pass the "already saved" check; the loser then
 * fails on the unique (scene, source url) index, which surfaces as a {@code ConflictException}, so
 * nothing is saved twice.
 */
public class SceneScoutingService {

    private static final Logger log = LoggerFactory.getLogger(SceneScoutingService.class);

    private final ScoutingPipeline pipeline;
    private final SceneRepository scenes;
    private final LocationRepository locations;
    private final BlockingTransactions db;
    private final ObjectMapper mapper;

    public SceneScoutingService(ScoutingPipeline pipeline, SceneRepository scenes, LocationRepository locations,
                                BlockingTransactions db, ObjectMapper mapper) {
        this.pipeline = pipeline;
        this.scenes = scenes;
        this.locations = locations;
        this.db = db;
        this.mapper = mapper;
    }

    /**
     * Extracts and stores the scene's requirements, replacing any earlier ones. If the model answers
     * but never usably, the scene is marked {@code FAILED}; if it could not be reached at all the
     * scene is left as it was, since nothing is known about the scene itself.
     */
    public Mono<SceneResponse> parseScene(UUID ownerId, UUID sceneId) {
        return db.call(() -> load(ownerId, sceneId).getSourceText())
                .flatMap(sourceText -> extractAndStore(ownerId, sceneId, sourceText));
    }

    /**
     * Finds and assesses venues for the scene and saves the new ones as {@code SUGGESTED} locations.
     * A scene that has not been parsed is parsed first. The project's location area is checked
     * before any model call is made.
     *
     * @throws ScoutingException (as an error signal) if the scene is not the owner's or the project
     *                           has no location area
     */
    public Mono<ScoutingResult> scout(UUID ownerId, UUID sceneId, int maxResults) {
        return db.call(() -> prepare(ownerId, sceneId))
                .flatMap(target -> requirementsFor(ownerId, sceneId, target)
                        .flatMap(requirements -> pipeline.scout(requirements, target.area(), maxResults))
                        .flatMap(outcome -> db.call(() -> saveVenues(ownerId, sceneId, outcome))
                                .onErrorMap(DataIntegrityViolationException.class, Conflicts::translate)));
    }

    // --- steps ----------------------------------------------------------------------------------

    private record Target(String sourceText, SceneRequirements requirements, String area) {
    }

    private Target prepare(UUID ownerId, UUID sceneId) {
        Scene scene = load(ownerId, sceneId);
        String area = scene.getProject().getLocationArea();
        if (area == null) {
            throw new ScoutingException(Kind.LOCATION_AREA_MISSING,
                    "The scene's project has no location area to search in; set one first");
        }
        return new Target(scene.getSourceText(), scene.requirements(), area);
    }

    private Mono<SceneRequirements> requirementsFor(UUID ownerId, UUID sceneId, Target target) {
        if (target.requirements() != null) {
            return Mono.just(target.requirements());
        }
        return extractAndStore(ownerId, sceneId, target.sourceText()).map(SceneResponse::requirements);
    }

    private Mono<SceneResponse> extractAndStore(UUID ownerId, UUID sceneId, String sourceText) {
        return pipeline.extractRequirements(sourceText)
                .flatMap(requirements -> db.call(() -> storeRequirements(ownerId, sceneId, requirements)))
                .onErrorResume(LlmException.class, error -> error.kind() == LlmException.Kind.INVALID_OUTPUT
                        ? markParseFailed(ownerId, sceneId).then(Mono.error(error))
                        : Mono.error(error));
    }

    private SceneResponse storeRequirements(UUID ownerId, UUID sceneId, SceneRequirements requirements) {
        Scene scene = load(ownerId, sceneId);
        // The typed answer re-serialised: extra fields the model may have added were already dropped.
        scene.applyRequirements(requirements, mapper.valueToTree(requirements));
        return SceneResponse.from(scenes.saveAndFlush(scene));
    }

    /** Best effort: failing to record the failure must not hide the failure that caused it. */
    private Mono<Void> markParseFailed(UUID ownerId, UUID sceneId) {
        return db.call(() -> {
                    Scene scene = load(ownerId, sceneId);
                    scene.markParseFailed();
                    scenes.saveAndFlush(scene);
                    return Boolean.TRUE;
                })
                .onErrorResume(error -> {
                    log.warn("Could not mark scene {} as parse-failed: {}", sceneId, error.toString());
                    return Mono.empty();
                })
                .then();
    }

    private ScoutingResult saveVenues(UUID ownerId, UUID sceneId, ScoutingOutcome outcome) {
        Scene scene = load(ownerId, sceneId);
        Set<String> seen = new HashSet<>(locations.findSourceUrlsBySceneId(sceneId));

        List<Location> toSave = new ArrayList<>();
        for (ScoutedVenue venue : outcome.venues()) {
            if (seen.add(venue.source().url())) {
                toSave.add(toLocation(scene, venue));
            }
        }
        List<LocationResponse> added = locations.saveAllAndFlush(toSave).stream().map(LocationResponse::from).toList();
        return new ScoutingResult(added, outcome.venues().size() - added.size(), outcome.unassessed());
    }

    private static Location toLocation(Scene scene, ScoutedVenue venue) {
        SearchResult source = venue.source();
        LocationAssessment assessment = venue.assessment();
        Location location = new Location(scene, source.title());
        location.setSourceUrl(source.url());
        location.setSourceProvider(source.provider());
        location.setSourceExcerpt(source.excerpt());
        location.setFitScore(assessment.fitScore().shortValue());
        location.setFitReason(assessment.fitReason());
        location.setBookingFriction(assessment.bookingFriction());
        location.setFrictionNote(assessment.frictionNote());
        location.setFootprintWarnings(new ArrayList<>(assessment.footprintWarnings()));
        return location;
    }

    private Scene load(UUID ownerId, UUID sceneId) {
        return scenes.findOwned(sceneId, ownerId)
                .orElseThrow(() -> new ScoutingException(Kind.SCENE_NOT_FOUND, "Scene " + sceneId + " not found"));
    }
}
