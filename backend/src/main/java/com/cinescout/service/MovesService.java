package com.cinescout.service;

import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.dto.MovesResponse;
import com.cinescout.dto.MovesResponse.Move;
import com.cinescout.dto.MovesResponse.Status;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.routing.Route;
import com.cinescout.logistics.routing.RouteCache;
import com.cinescout.logistics.routing.RoutingClient;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.SceneRepository;
import com.cinescout.resilience.Guard;
import com.cinescout.resilience.GuardFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Company moves between a shoot day's venues. Each pair of places is asked of the router once and kept
 * ({@link RouteCache}); a request asks for at most {@code max-lookups} new ones, one after another through the shared
 * pacer, and the rest show as pending until the next load.
 */
@Service
public class MovesService {

    private static final Logger log = LoggerFactory.getLogger(MovesService.class);

    private final SceneRepository scenes;
    private final LocationRepository locations;
    private final RouteCache cache;
    private final RoutingClient router;
    private final Guard guard;
    private final ProjectAccess access;
    private final BlockingTransactions db;
    private final Duration warnAfter;
    private final int maxLookups;

    public MovesService(SceneRepository scenes, LocationRepository locations, RouteCache cache, RoutingClient router, GuardFactory guards,
                        ProjectAccess access, BlockingTransactions db,
                        @Value("${cinescout.moves.warn-after:60m}") Duration warnAfter,
                        @Value("${cinescout.moves.max-lookups:8}") int maxLookups) {
        this.scenes = scenes;
        this.locations = locations;
        this.cache = cache;
        this.router = router;
        this.guard = guards.create("routing",
                error -> error instanceof LogisticsException e && e.isRetryable(),
                error -> error instanceof LogisticsException e && e.kind() != LogisticsException.Kind.INVALID_REQUEST,
                open -> new LogisticsException(LogisticsException.Kind.UNAVAILABLE, "The routing circuit breaker is open", open));
        this.access = access;
        this.db = db;
        this.warnAfter = warnAfter;
        this.maxLookups = maxLookups;
    }

    /**
     * The project's moves for a member, asking the router for what is not known yet.
     *
     * @param permit asked once before the first new lookup (the user's allowance of lookups)
     */
    public Mono<MovesResponse> moves(UUID userId, UUID projectId, Supplier<Mono<Void>> permit) {
        return db.call(() -> {
                    access.project(userId, projectId, ProjectRole.VIEWER);
                    return plan(userId, projectId);
                })
                .flatMap(plan -> fill(plan, permit))
                .map(this::respond);
    }

    /** The moves known so far, asking nobody: for the shared call sheet. {@code ownerId} sees the project. */
    public Mono<MovesResponse> cachedMoves(UUID ownerId, UUID projectId) {
        return db.call(() -> plan(ownerId, projectId)).map(this::respond);
    }

    // --- the day's legs ----------------------------------------------------------------------------------

    /** One leg of a day; {@code route} null while not known, {@code status} as far as known. */
    private static final class Leg {
        final LocalDate date;
        final Location from;
        final Location to;
        final Scene fromScene;
        final Scene toScene;
        Status status;
        Route route;

        Leg(LocalDate date, Scene fromScene, Location from, Scene toScene, Location to) {
            this.date = date;
            this.fromScene = fromScene;
            this.from = from;
            this.toScene = toScene;
            this.to = to;
        }

        GeoPoint fromPoint() {
            return point(from);
        }

        GeoPoint toPoint() {
            return point(to);
        }
    }

    private List<Leg> plan(UUID userId, UUID projectId) {
        Map<UUID, List<Location>> confirmed = locations
                .findVisibleByProjectAndStatus(projectId, userId, LocationStatus.CONFIRMED, Pageable.unpaged())
                .stream().collect(Collectors.groupingBy(location -> location.getScene().getId(), LinkedHashMap::new, Collectors.toList()));
        List<Scene> all = scenes.findVisibleByProject(projectId, userId, Pageable.unpaged()).getContent();

        // Each day's stops: the scenes shooting that day by call time (then script order), each with its venues.
        Map<LocalDate, List<Scene>> byDay = new TreeMap<>();
        for (Scene scene : all) {
            if (confirmed.containsKey(scene.getId())) {
                ScheduleConflicts.days(scene).forEach(day -> byDay.computeIfAbsent(day, d -> new ArrayList<>()).add(scene));
            }
        }
        List<Leg> legs = new ArrayList<>();
        byDay.forEach((date, dayScenes) -> {
            List<Scene> ordered = dayScenes.stream()
                    .sorted(Comparator.comparing(Scene::getCallTime, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            Scene previousScene = null;
            Location previous = null;
            for (Scene scene : ordered) {
                for (Location venue : confirmed.get(scene.getId())) {
                    if (previous != null && !previous.getId().equals(venue.getId()) && !ScheduleConflicts.sameVenue(previous, venue)) {
                        legs.add(new Leg(date, previousScene, previous, scene, venue));
                    }
                    previousScene = scene;
                    previous = venue;
                }
            }
        });
        for (Leg leg : legs) {
            if (leg.fromPoint() == null || leg.toPoint() == null) {
                leg.status = Status.UNPLACED;
                continue;
            }
            Optional<Optional<Route>> known = cache.find(leg.fromPoint(), leg.toPoint());
            if (known.isPresent()) {
                leg.route = known.get().orElse(null);
                leg.status = leg.route == null ? Status.NO_ROUTE : Status.OK;
            } else {
                leg.status = Status.PENDING;
            }
        }
        return legs;
    }

    /** Asks the router for up to {@code maxLookups} pending legs, one at a time, keeping each answer. */
    private Mono<List<Leg>> fill(List<Leg> legs, Supplier<Mono<Void>> permit) {
        List<Leg> pending = legs.stream().filter(leg -> leg.status == Status.PENDING).limit(maxLookups).toList();
        if (pending.isEmpty()) {
            return Mono.just(legs);
        }
        return permit.get()
                .thenMany(Flux.fromIterable(pending).concatMap(this::lookUp))
                .then(Mono.just(legs));
    }

    private Mono<Leg> lookUp(Leg leg) {
        return guard.call(() -> router.drive(leg.fromPoint(), leg.toPoint()))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(route -> db.call(() -> {
                    cache.put(leg.fromPoint(), leg.toPoint(), route.orElse(null));
                    leg.route = route.orElse(null);
                    leg.status = route.isPresent() ? Status.OK : Status.NO_ROUTE;
                    return leg;
                }))
                .onErrorResume(error -> {
                    log.info("Routing failed for a company move: {}", error.toString());
                    leg.status = Status.UNAVAILABLE;
                    return Mono.just(leg);
                });
    }

    private MovesResponse respond(List<Leg> legs) {
        Map<LocalDate, List<Move>> byDay = new TreeMap<>();
        for (Leg leg : legs) {
            byDay.computeIfAbsent(leg.date, d -> new ArrayList<>()).add(move(leg));
        }
        List<MovesResponse.Day> days = byDay.entrySet().stream().map(e -> new MovesResponse.Day(e.getKey(), e.getValue())).toList();
        return new MovesResponse(days, (int) warnAfter.toMinutes(), router.attribution());
    }

    private Move move(Leg leg) {
        String between = leg.from.getName() + " to " + leg.to.getName();
        if (leg.status != Status.OK) {
            String why = switch (leg.status) {
                case UNPLACED -> "set both venues' pins to work out the drive";
                case NO_ROUTE -> "no road between them";
                case PENDING -> "not worked out yet";
                default -> "the routing service could not be reached; try again later";
            };
            return new Move(leg.from.getId(), leg.from.getName(), leg.fromScene.getId(), leg.to.getId(), leg.to.getName(), leg.toScene.getId(),
                    leg.status, null, null, false, between + ": " + why);
        }
        int minutes = (int) Math.ceil(leg.route.durationSeconds() / 60);
        double kilometres = Math.round(leg.route.distanceMeters() / 100) / 10.0;
        boolean tooLong = minutes > warnAfter.toMinutes();
        return new Move(leg.from.getId(), leg.from.getName(), leg.fromScene.getId(), leg.to.getId(), leg.to.getName(), leg.toScene.getId(),
                Status.OK, minutes, kilometres, tooLong, String.format(Locale.ROOT, "%s: %s, %.1f km by road", between, duration(minutes), kilometres));
    }

    /** "21 min", "1 h 05 min". */
    static String duration(int minutes) {
        return minutes < 60 ? minutes + " min" : "%d h %02d min".formatted(minutes / 60, minutes % 60);
    }

    private static GeoPoint point(Location location) {
        return location.getLatitude() == null || location.getLongitude() == null ? null
                : new GeoPoint(location.getLatitude().doubleValue(), location.getLongitude().doubleValue());
    }
}
