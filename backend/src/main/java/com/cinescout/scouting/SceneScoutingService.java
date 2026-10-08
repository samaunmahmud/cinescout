package com.cinescout.scouting;

import com.cinescout.ai.LocationAssessment;
import com.cinescout.ai.SearchResult;
import com.cinescout.domain.ActivityTarget;
import com.cinescout.domain.ActivityVerb;
import com.cinescout.domain.Location;
import com.cinescout.domain.ParseStatus;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.domain.ScoutFilters;
import com.cinescout.domain.VenueNames;
import com.cinescout.dto.LocationResponse;
import com.cinescout.dto.SceneResponse;
import com.cinescout.llm.LlmException;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.SceneRepository;
import com.cinescout.scouting.ScoutingException.Kind;
import com.cinescout.search.SearchHints;
import com.cinescout.service.ActivityLog;
import com.cinescout.service.Conflicts;
import com.cinescout.service.NotFoundException;
import com.cinescout.service.ProjectAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Scouting for a persisted scene: runs the {@link ScoutingPipeline} and stores what it finds.
 *
 * <p>JPA is blocking, so every database step runs on {@code boundedElastic} in its own short
 * transaction, never on an event-loop thread and never spanning an LLM or search call (those take
 * seconds). Every step re-checks that the scene belongs to {@code userId}, so a scene deleted or
 * reassigned mid-run fails cleanly instead of being written to.
 *
 * <p>New venues are placed on the map before they are saved (by the address the page gives, or their
 * name in the search area), on a best-effort basis; see {@link VenuePlacer}. Venues already saved (the same
 * page, or a location of the same name) are not looked up again.
 *
 * <p>Two runs racing on the same scene can both pass the "already saved" check; the loser then
 * fails on the unique (scene, source url) index, which surfaces as a {@code ConflictException}, so
 * nothing is saved twice.
 */
public class SceneScoutingService {

    private static final Logger log = LoggerFactory.getLogger(SceneScoutingService.class);

    /** The columns take any length, but the API caps what users enter; scouted values get the same caps. */
    private static final int MAX_NAME = 200;
    private static final int MAX_ADDRESS = 500;

    /** How many scenes one batch analyses: each is a model call of a few seconds, and the request waits for them all. */
    /** How many reasons for passing on venues steer a run, and how many venues already found the search is told of. */
    static final int MAX_AVOID = 5;
    static final int MAX_KNOWN_VENUES = 15;

    public static final int MAX_BATCH = 20;
    private static final int BATCH_CONCURRENCY = 4;

    private final ScoutingPipeline pipeline;
    private final VenuePlacer placer;
    private final SceneRepository scenes;
    private final ProjectRepository projects;
    private final LocationRepository locations;
    private final ProjectAccess access;
    private final BlockingTransactions db;
    private final ObjectMapper mapper;
    private final ActivityLog activity;

    public SceneScoutingService(ScoutingPipeline pipeline, VenuePlacer placer, SceneRepository scenes, ProjectRepository projects,
                                LocationRepository locations, ProjectAccess access, BlockingTransactions db, ObjectMapper mapper,
                                ActivityLog activity) {
        this.activity = activity;
        this.pipeline = pipeline;
        this.placer = placer;
        this.scenes = scenes;
        this.projects = projects;
        this.locations = locations;
        this.access = access;
        this.db = db;
        this.mapper = mapper;
    }

    /**
     * Extracts and stores the scene's requirements, replacing any earlier ones. If the model answers
     * but never usably, the scene is marked {@code FAILED}; if it could not be reached at all the
     * scene is left as it was, since nothing is known about the scene itself.
     */
    public Mono<SceneResponse> parseScene(UUID userId, UUID sceneId) {
        return db.call(() -> Map.entry(load(userId, sceneId).getSourceText(), avoidFor(sceneId)))
                .flatMap(scene -> extractAndStore(userId, sceneId, scene.getKey(), scene.getValue()));
    }

    /**
     * Analyses the project's scenes that have not been analysed yet, in script order, at most
     * {@value #MAX_BATCH} a run (the rest are reported as remaining, for the next run). Scenes whose analysis
     * failed before are left alone: they are retried one at a time, by choice.
     *
     * <p>Each scene is one model call, so each needs a {@code permit} (the caller's rate limit). A scene the
     * model cannot make sense of is marked {@code FAILED} and the run goes on. When the permits or the AI
     * service give out, the scenes not reached simply stay as they were; only if that leaves the run with
     * nothing at all to show is the cause emitted as the error.
     *
     * @param permit subscribed to once per scene, before its model call; an error skips the scene
     * @throws NotFoundException (as an error signal) if the project is not the owner's
     */
    public Mono<BatchParseResult> parseProject(UUID userId, UUID projectId, Supplier<Mono<Void>> permit) {
        return db.call(() -> pendingScenes(userId, projectId))
                .flatMapMany(Flux::fromIterable)
                .flatMap(sceneId -> parseOne(userId, sceneId, permit), BATCH_CONCURRENCY)
                .collectList()
                .flatMap(attempts -> {
                    int parsed = (int) attempts.stream().filter(attempt -> attempt.outcome() == Attempt.Outcome.PARSED).count();
                    int failed = (int) attempts.stream().filter(attempt -> attempt.outcome() == Attempt.Outcome.FAILED).count();
                    Throwable blocker = attempts.stream().map(Attempt::blocker).filter(Objects::nonNull).findFirst().orElse(null);
                    if (parsed + failed == 0 && blocker != null) {
                        return Mono.error(blocker);
                    }
                    return db.call(() -> new BatchParseResult(parsed, failed,
                            (int) scenes.countByProjectIdAndParseStatus(projectId, ParseStatus.PENDING)));
                });
    }

    /**
     * Finds and assesses venues for the scene and saves the new ones as {@code SUGGESTED} locations.
     * A scene that has not been parsed is parsed first. The project's location area is checked
     * before any model call is made.
     *
     * @throws ScoutingException (as an error signal) if the scene is not the owner's or the project
     *                           has no location area
     */
    public Mono<ScoutingResult> scout(UUID userId, UUID sceneId, int maxResults) {
        return scout(userId, sceneId, maxResults, null);
    }

    /**
     * As {@link #scout(UUID, UUID, int)}, keeping to {@code filters}, or to the project's own when null. Venues
     * placed further from the base point than the radius are left out once placed; ones that cannot be placed stay,
     * as nothing says they are far.
     */
    public Mono<ScoutingResult> scout(UUID userId, UUID sceneId, int maxResults, ScoutFilters filters) {
        return db.call(() -> prepare(userId, sceneId, filters))
                .flatMap(target -> basePoint(target.filters())
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .flatMap(base -> requirementsFor(userId, sceneId, target)
                                .flatMap(requirements -> pipeline.scout(requirements, target.area(), maxResults,
                                        hints(target.filters(), target.known(), target.avoid()), target.filters()))
                                .flatMap(outcome -> db.call(() -> unsaved(userId, sceneId, outcome))
                                        .flatMap(fresh -> placer.place(fresh, target.area()))
                                        .flatMap(placed -> db.call(() -> saveVenues(userId, sceneId,
                                                withinRadius(outcome, placed, base.orElse(null), target.filters()), placed)))
                                        .onErrorMap(DataIntegrityViolationException.class, Conflicts::translate))));
    }

    /**
     * Has the model assess a venue already on the scene (one added by hand or from the library, say) against the
     * scene's requirements, as scouting assesses what it finds, and stores the fit score, booking route and
     * warnings on it. Name, address, pin, status and notes stay as they are. A scene not analysed yet is analysed
     * first.
     */
    public Mono<LocationResponse> assessVenue(UUID userId, UUID locationId) {
        return db.call(() -> {
                    Location location = access.location(userId, locationId, ProjectRole.EDITOR);
                    Scene scene = location.getScene();
                    return new Assessing(scene.getId(), scene.getSourceText(), scene.requirements(),
                            Objects.requireNonNullElse(scene.getProject().getLocationArea(), "not given"),
                            venueFacts(location), avoidFor(scene.getId()));
                })
                .flatMap(target -> (target.requirements() != null
                        ? Mono.just(target.requirements())
                        : extractAndStore(userId, target.sceneId(), target.sourceText(), target.avoid()).map(SceneResponse::requirements))
                        .flatMap(requirements -> pipeline.assessOne(requirements, target.area(), target.venue(), target.avoid())))
                .flatMap(assessment -> db.call(() -> {
                    Location location = access.location(userId, locationId, ProjectRole.EDITOR);
                    location.setFitScore(assessment.fitScore().shortValue());
                    location.setFitReason(assessment.fitReason());
                    location.setBookingFriction(assessment.bookingFriction());
                    location.setFrictionNote(assessment.frictionNote());
                    location.setFootprintWarnings(new ArrayList<>(assessment.footprintWarnings()));
                    return LocationResponse.from(locations.saveAndFlush(location));
                }));
    }

    private record Assessing(UUID sceneId, String sourceText, SceneRequirements requirements, String area, SearchResult venue,
                             List<String> avoid) {
    }

    /** What is known of a saved venue, shaped as a search hit for the assessment prompt. */
    static SearchResult venueFacts(Location location) {
        StringBuilder facts = new StringBuilder();
        append(facts, "Address", location.getAddress());
        append(facts, "The crew's notes", location.getNotes());
        append(facts, "From its web page", location.getSourceExcerpt());
        append(facts, "Quoted price", location.getQuote());
        String url = location.getSourceUrl() != null ? location.getSourceUrl() : "none: the crew added this venue by hand";
        String provider = location.getSourceProvider() != null ? location.getSourceProvider() : "manual";
        return new SearchResult(location.getName(), url, facts.isEmpty() ? null : facts.toString().strip(), provider);
    }

    private static void append(StringBuilder out, String label, String value) {
        if (value != null && !value.isBlank()) {
            out.append(label).append(": ").append(value.strip()).append('\n');
        }
    }

    /** The base point a radius is measured from: the spot picked on the map, or the address found on it. */
    private Mono<GeoPoint> basePoint(ScoutFilters filters) {
        if (filters.radiusKm() == null) {
            return Mono.empty();
        }
        GeoPoint picked = filters.pickedPoint();
        return picked != null ? Mono.just(picked) : placer.locate(filters.baseAddress());
    }

    static SearchHints hints(ScoutFilters filters, List<String> known, List<String> avoid) {
        GeoPoint picked = filters.pickedPoint();
        String near = picked != null ? String.format(Locale.ROOT, "%.5f, %.5f", picked.latitude(), picked.longitude()) : filters.baseAddress();
        return new SearchHints(near, near == null ? null : filters.radiusKm(), filters.excludedTypes(), filters.privateAllowed(),
                known, avoid);
    }

    /** The outcome without the venues placed further from {@code base} than the radius, counted as such. */
    static ScoutingOutcome withinRadius(ScoutingOutcome outcome, Map<String, GeoPoint> placed, GeoPoint base, ScoutFilters filters) {
        if (base == null || filters.radiusKm() == null) {
            return outcome;
        }
        double limitMeters = filters.radiusKm() * 1000;
        List<ScoutedVenue> kept = outcome.venues().stream()
                .filter(venue -> {
                    GeoPoint point = placed.get(venue.source().url());
                    return point == null || point.distanceTo(base) <= limitMeters;
                })
                .toList();
        return new ScoutingOutcome(kept, outcome.unassessed(), outcome.notVenues(), outcome.unsuitable(),
                outcome.filtered().plusOutsideRadius(outcome.venues().size() - kept.size()));
    }

    // --- steps ----------------------------------------------------------------------------------

    private List<UUID> pendingScenes(UUID userId, UUID projectId) {
        access.project(userId, projectId, ProjectRole.EDITOR);
        return scenes.findIdsByProjectAndParseStatus(projectId, ParseStatus.PENDING, PageRequest.of(0, MAX_BATCH));
    }

    /** How one scene of a batch went; {@code blocker} is why it was not attempted or could not be answered. */
    private record Attempt(Outcome outcome, Throwable blocker) {
        enum Outcome { PARSED, FAILED, NOT_DONE }

        static final Attempt PARSED = new Attempt(Outcome.PARSED, null);
        static final Attempt FAILED = new Attempt(Outcome.FAILED, null);
    }

    private Mono<Attempt> parseOne(UUID userId, UUID sceneId, Supplier<Mono<Void>> permit) {
        return permit.get()
                .then(Mono.defer(() -> parseScene(userId, sceneId)))
                .thenReturn(Attempt.PARSED)
                .onErrorResume(error -> {
                    if (error instanceof LlmException llm && llm.kind() == LlmException.Kind.INVALID_OUTPUT) {
                        return Mono.just(Attempt.FAILED);
                    }
                    log.info("Scene {} left unanalysed in a batch: {}", sceneId, error.toString());
                    return Mono.just(new Attempt(Attempt.Outcome.NOT_DONE, error));
                });
    }

    private record Target(String sourceText, SceneRequirements requirements, String area, ScoutFilters filters,
                          List<String> known, List<String> avoid) {
    }

    private Target prepare(UUID userId, UUID sceneId, ScoutFilters override) {
        Scene scene = load(userId, sceneId);
        String area = scene.getProject().getLocationArea();
        if (area == null) {
            throw new ScoutingException(Kind.LOCATION_AREA_MISSING,
                    "The scene's project has no location area to search in; set one first");
        }
        ScoutFilters filters = override != null ? override
                : scene.getProject().getScoutFilters() != null ? scene.getProject().getScoutFilters() : ScoutFilters.NONE;
        List<String> known = locations.findSavedVenues(sceneId).stream()
                .map(LocationRepository.SavedVenueRow::getName)
                .limit(MAX_KNOWN_VENUES)
                .toList();
        return new Target(scene.getSourceText(), scene.requirements(), area, filters, known, avoidFor(sceneId));
    }

    private Mono<SceneRequirements> requirementsFor(UUID userId, UUID sceneId, Target target) {
        if (target.requirements() != null) {
            return Mono.just(target.requirements());
        }
        return extractAndStore(userId, sceneId, target.sourceText(), target.avoid()).map(SceneResponse::requirements);
    }

    /**
     * Why the crew passed on the scene's venues: the latest {@value #MAX_AVOID} distinct reasons (ignoring case),
     * newest first. They steer the next extraction, search and assessments away from the same problems.
     */
    List<String> avoidFor(UUID sceneId) {
        Set<String> seen = new HashSet<>();
        List<String> reasons = new ArrayList<>();
        for (String reason : locations.findRejectionReasons(sceneId, PageRequest.of(0, MAX_AVOID * 4))) {
            String key = reason.strip().toLowerCase(Locale.ROOT);
            if (!key.isEmpty() && seen.add(key) && reasons.size() < MAX_AVOID) {
                reasons.add(reason.strip());
            }
        }
        return reasons;
    }

    private Mono<SceneResponse> extractAndStore(UUID userId, UUID sceneId, String sourceText, List<String> avoid) {
        return pipeline.extractRequirements(sourceText, avoid)
                .flatMap(requirements -> db.call(() -> storeRequirements(userId, sceneId, requirements)))
                .onErrorResume(LlmException.class, error -> error.kind() == LlmException.Kind.INVALID_OUTPUT
                        ? markParseFailed(userId, sceneId).then(Mono.error(error))
                        : Mono.error(error));
    }

    private SceneResponse storeRequirements(UUID userId, UUID sceneId, SceneRequirements requirements) {
        Scene scene = load(userId, sceneId);
        // The typed answer re-serialised: extra fields the model may have added were already dropped.
        scene.applyRequirements(requirements, mapper.valueToTree(requirements));
        return SceneResponse.from(scenes.saveAndFlush(scene));
    }

    /** Best effort: failing to record the failure must not hide the failure that caused it. */
    private Mono<Void> markParseFailed(UUID userId, UUID sceneId) {
        return db.call(() -> {
                    Scene scene = load(userId, sceneId);
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

    /** The venues not saved for the scene yet: only these are worth placing on the map. */
    private List<ScoutedVenue> unsaved(UUID userId, UUID sceneId, ScoutingOutcome outcome) {
        load(userId, sceneId);
        Saved saved = saved(sceneId);
        return outcome.venues().stream().filter(saved::addIfNew).toList();
    }

    /**
     * What a scene already has. A venue counts as saved by the same rules that make two finds of one run the same
     * venue ({@link ScoutingPipeline#sameVenueOnce}): its page is saved, or a location of the same name, at the same
     * street address, or the same listing on a booking site under another path.
     */
    private Saved saved(UUID sceneId) {
        Saved saved = new Saved(new HashSet<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (LocationRepository.SavedVenueRow row : locations.findSavedVenues(sceneId)) {
            saved.remember(row.getSourceUrl(), row.getName(), row.getAddress());
        }
        return saved;
    }

    private record Saved(Set<String> urls, List<String> pages, List<String> names, List<String> addresses) {
        /** True, and remembers it, if {@code venue} is not saved yet. */
        boolean addIfNew(ScoutedVenue venue) {
            String url = venue.source().url();
            String name = venue.assessment().venueName();
            String address = venue.assessment().address();
            if (urls.contains(url)
                    || names.stream().anyMatch(saved -> VenueNames.sameVenue(saved, name))
                    || addresses.stream().anyMatch(saved -> VenueNames.sameStreetAddress(saved, address))
                    || pages.stream().anyMatch(saved -> ScoutingPipeline.sameListing(saved, url))) {
                return false;
            }
            remember(url, name, address);
            return true;
        }

        void remember(String url, String name, String address) {
            if (url != null) {
                urls.add(url);
                pages.add(url);
            }
            if (name != null) {
                names.add(name);
            }
            if (address != null) {
                addresses.add(address);
            }
        }
    }

    private ScoutingResult saveVenues(UUID userId, UUID sceneId, ScoutingOutcome outcome, Map<String, GeoPoint> placed) {
        Scene scene = load(userId, sceneId);
        // Checked again: another run may have saved some of these while they were being placed.
        Saved saved = saved(sceneId);

        List<Location> toSave = new ArrayList<>();
        for (ScoutedVenue venue : outcome.venues()) {
            if (saved.addIfNew(venue)) {
                toSave.add(toLocation(scene, venue, placed.get(venue.source().url())));
            }
        }
        List<LocationResponse> added = locations.saveAllAndFlush(toSave).stream().map(LocationResponse::from).toList();
        activity.record(scene.getProject(), userId, ActivityVerb.SCOUTED, ActivityTarget.SCENE, scene.getId(),
                ActivityLog.facts("scene", scene.getTitle(), "added", added.size()));
        return new ScoutingResult(added, outcome.venues().size() - added.size(), outcome.unassessed(), outcome.notVenues(), outcome.unsuitable(),
                outcome.filtered());
    }

    private static Location toLocation(Scene scene, ScoutedVenue venue, GeoPoint point) {
        SearchResult source = venue.source();
        LocationAssessment assessment = venue.assessment();
        String name = assessment.venueName() != null ? assessment.venueName() : source.title();
        Location location = new Location(scene, cap(name, MAX_NAME));
        location.setAddress(cap(assessment.address(), MAX_ADDRESS));
        if (point != null) {
            location.setLatitude(degrees(point.latitude()));
            location.setLongitude(degrees(point.longitude()));
        }
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

    private static String cap(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max).strip();
    }

    /** Six decimal places, about 10 cm, as the columns store them. */
    private static BigDecimal degrees(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Parsing and scouting change the scene and spend the AI allowance: an editor's work. A scene the user cannot see
     * is reported in scouting's own terms, which the API answers with the same 404.
     */
    private Scene load(UUID userId, UUID sceneId) {
        try {
            return access.scene(userId, sceneId, ProjectRole.EDITOR);
        } catch (NotFoundException e) {
            throw new ScoutingException(Kind.SCENE_NOT_FOUND, "Scene " + sceneId + " not found");
        }
    }
}
