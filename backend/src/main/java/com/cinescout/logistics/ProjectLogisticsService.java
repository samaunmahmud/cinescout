package com.cinescout.logistics;

import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.Scene;
import com.cinescout.dto.ScheduleResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.service.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Works out the logistics of a project's confirmed venues that do not have them yet (or whose report predates the
 * scene's current dates), so the schedule and the call
 * sheet can show the light and the weather on each shoot day. One venue at a time: each is several calls to free
 * public services, whose fair-use rules the per-user lookup allowance keeps.
 */
@Service
public class ProjectLogisticsService {

    private static final Logger log = LoggerFactory.getLogger(ProjectLogisticsService.class);

    /**
     * How many venues one run takes. Each usually takes seconds, but up to half a minute when the public map
     * server is busy, and the request waits for them all: six keeps the worst case within the five minutes the
     * web server in front waits for an answer (nginx's proxy_read_timeout).
     */
    public static final int MAX_BATCH = 6;

    private final LogisticsService logistics;
    private final ProjectRepository projects;
    private final LocationRepository locations;
    private final BlockingTransactions db;

    public ProjectLogisticsService(LogisticsService logistics, ProjectRepository projects, LocationRepository locations, BlockingTransactions db) {
        this.logistics = logistics;
        this.projects = projects;
        this.locations = locations;
        this.db = db;
    }

    /**
     * @param permit subscribed to once per venue before its lookups (the caller's rate limit); an error skips it
     * @throws NotFoundException (as an error signal) if the project is not the owner's
     */
    public Mono<BatchLogisticsResult> refreshConfirmed(UUID ownerId, UUID projectId, Supplier<Mono<Void>> permit) {
        return db.call(() -> missing(ownerId, projectId).stream().limit(MAX_BATCH).map(Location::getId).toList())
                .flatMapMany(Flux::fromIterable)
                .concatMap(locationId -> permit.get()
                        .then(Mono.defer(() -> logistics.refresh(ownerId, locationId)))
                        .thenReturn(Attempt.DONE)
                        .onErrorResume(error -> {
                            log.info("Logistics of location {} not worked out in a batch: {}", locationId, error.toString());
                            return Mono.just(new Attempt(error));
                        }))
                .collectList()
                .flatMap(attempts -> {
                    int updated = (int) attempts.stream().filter(attempt -> attempt == Attempt.DONE).count();
                    List<Throwable> errors = attempts.stream().map(Attempt::error).filter(Objects::nonNull).toList();
                    if (updated == 0 && !errors.isEmpty()) {
                        return Mono.error(errors.getFirst());
                    }
                    return db.call(() -> new BatchLogisticsResult(updated, errors.size(), missing(ownerId, projectId).size()));
                });
    }

    private List<Location> missing(UUID ownerId, UUID projectId) {
        projects.findByIdAndOwnerId(projectId, ownerId).orElseThrow(() -> new NotFoundException("Project", projectId));
        return locations.findOwnedByProjectAndStatus(projectId, ownerId, LocationStatus.CONFIRMED, Pageable.unpaged())
                .stream().filter(ProjectLogisticsService::needsLogistics).toList();
    }

    /**
     * No report yet, or one worked out before the scene was given (other) dates: its days do not include the one
     * the scene is now shot on, so a call sheet would have nothing to say about it.
     */
    private static boolean needsLogistics(Location location) {
        if (location.getLogisticsJson() == null) {
            return true;
        }
        Scene scene = location.getScene();
        LocalDate day = scene.getShootDateStart() != null ? scene.getShootDateStart() : scene.getShootDateEnd();
        return day != null && ScheduleResponse.DayConditions.of(location.getLogisticsJson(), day) == null;
    }

    private record Attempt(Throwable error) {
        static final Attempt DONE = new Attempt(null);
    }

    /**
     * @param updated   venues whose logistics were worked out
     * @param failed    venues that could not be worked out (not found on the map, say, or the service down)
     * @param remaining confirmed venues still without logistics, the failed ones included
     */
    public record BatchLogisticsResult(int updated, int failed, int remaining) {
    }
}
