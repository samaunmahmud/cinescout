package com.cinescout.jobs;

import com.cinescout.domain.AlertKind;
import com.cinescout.domain.DatabaseTime;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.Scene;
import com.cinescout.domain.SceneCover;
import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.LogisticsException;
import com.cinescout.logistics.weather.WeatherClient;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.AlertRepository;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.SceneCoverRepository;
import com.cinescout.resilience.Guard;
import com.cinescout.resilience.GuardFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The weather watch: fetches the forecast at every confirmed venue whose scene shoots in the next seven days and alerts
 * the project's crew to each shoot day where the chance of rain or the wind crosses the project's thresholds, naming the
 * scene's cover sets. One alert per venue and day, however often it runs. Venues are fetched one after another, at most
 * {@link JobsProperties#weatherVenues()} a run, the earliest shoot first; one that fails is skipped until the next run.
 * Also forgets alerts older than 90 days.
 */
@Component
class WeatherWatchJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(WeatherWatchJob.class);

    /** Today and the six days after it. */
    static final int DAYS = 7;
    private static final Duration KEEP_ALERTS = Duration.ofDays(90);

    private final LocationRepository locations;
    private final SceneCoverRepository covers;
    private final AlertRepository alerts;
    private final WeatherClient weather;
    private final Guard guard;
    private final BlockingTransactions db;
    private final ObjectMapper json;
    private final JobsProperties props;

    WeatherWatchJob(LocationRepository locations, SceneCoverRepository covers, AlertRepository alerts, WeatherClient weather, GuardFactory guards,
                    BlockingTransactions db, ObjectMapper json, JobsProperties props) {
        this.locations = locations;
        this.covers = covers;
        this.alerts = alerts;
        this.weather = weather;
        // Shares the "weather" breaker with the logistics reports: an outage seen by one spares the provider for the other.
        this.guard = guards.create("weather",
                error -> error instanceof LogisticsException e && e.isRetryable(),
                error -> error instanceof LogisticsException e && e.kind() != LogisticsException.Kind.INVALID_REQUEST,
                open -> new LogisticsException(LogisticsException.Kind.UNAVAILABLE, "The weather circuit breaker is open; the call was not made", open));
        this.db = db;
        this.json = json;
        this.props = props;
    }

    @Override
    public String name() {
        return "weather-watch";
    }

    @Override
    public Mono<JobRun> run() {
        LocalDate today = LocalDate.ofInstant(DatabaseTime.now(), ZoneOffset.UTC);
        LocalDate last = today.plusDays(DAYS - 1);
        return db.call(() -> watches(today, last))
                .flatMapMany(Flux::fromIterable)
                .concatMap(watch -> guard.call(() -> weather.forecast(watch.point(), watch.from(), watch.to()))
                        .map(series -> new Found(watch, WeatherBreach.find(series.days(), watch.from(), watch.to(), watch.rainThreshold(), watch.windThreshold())))
                        .onErrorResume(e -> {
                            log.warn("Weather watch skipped location {}: {}", watch.locationId(), e.toString());
                            return Mono.empty();
                        }))
                .collectList()
                .flatMap(found -> db.call(() -> raise(found)))
                .map(made -> {
                    if (made > 0) {
                        log.info("Weather watch raised {} alert(s)", made);
                    }
                    return new JobRun(name(), made, DatabaseTime.now());
                });
    }

    /** What to fetch, as plain values: entities do not leave the transaction. */
    private List<Watch> watches(LocalDate today, LocalDate last) {
        List<Location> venues = locations.findShootingBetween(today, last, PageRequest.of(0, props.weatherVenues()));
        Map<UUID, List<Map<String, Object>>> coversByScene = venues.isEmpty() ? Map.of()
                : covers.findByScenes(venues.stream().map(venue -> venue.getScene().getId()).distinct().toList()).stream()
                .filter(cover -> cover.getLocation().getStatus() != LocationStatus.CONFIRMED)
                .collect(Collectors.groupingBy(cover -> cover.getScene().getId(), Collectors.mapping(WeatherWatchJob::coverFacts, Collectors.toList())));
        return venues.stream().map(venue -> {
            Scene scene = venue.getScene();
            LocalDate start = scene.getShootDateStart() != null ? scene.getShootDateStart() : scene.getShootDateEnd();
            LocalDate end = scene.getShootDateEnd() != null ? scene.getShootDateEnd() : start;
            return new Watch(scene.getProject().getId(), scene.getId(), scene.getTitle(), venue.getId(), venue.getName(),
                    new GeoPoint(venue.getLatitude().doubleValue(), venue.getLongitude().doubleValue()),
                    start.isBefore(today) ? today : start, end.isAfter(last) ? last : end,
                    scene.getProject().getRainAlertPercent(), scene.getProject().getWindAlertKmh(),
                    coversByScene.getOrDefault(scene.getId(), List.of()));
        }).toList();
    }

    private static Map<String, Object> coverFacts(SceneCover cover) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("locationId", cover.getLocation().getId().toString());
        facts.put("name", cover.getLocation().getName());
        facts.put("trigger", cover.getTrigger());
        return facts;
    }

    private int raise(List<Found> found) {
        int made = 0;
        for (Found venue : found) {
            Watch watch = venue.watch();
            for (WeatherBreach breach : venue.breaches()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("scene", watch.sceneTitle());
                payload.put("venue", watch.venueName());
                payload.put("day", breach.day().toString());
                payload.put("reasons", breach.reasons());
                payload.put("rainChance", breach.rainChance());
                payload.put("rainMm", breach.rainMm());
                payload.put("rainThreshold", watch.rainThreshold());
                payload.put("windKmh", breach.windKmh() == null ? null : Math.round(breach.windKmh()));
                payload.put("gustKmh", breach.gustKmh() == null ? null : Math.round(breach.gustKmh()));
                payload.put("windThreshold", watch.windThreshold());
                payload.put("covers", watch.covers());
                made += alerts.raiseForCrew(watch.projectId(), AlertKind.WEATHER.name(), watch.sceneId(), watch.locationId(), write(payload),
                        "weather:" + watch.locationId() + ":" + breach.day());
            }
        }
        alerts.deleteOlderThan(DatabaseTime.now().minus(KEEP_ALERTS));
        return made;
    }

    private String write(Map<String, Object> payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("An alert's facts could not be written as JSON", e);
        }
    }

    /** A confirmed venue to watch from {@code from} to {@code to}, its scene's shoot days within the next week. */
    private record Watch(UUID projectId, UUID sceneId, String sceneTitle, UUID locationId, String venueName, GeoPoint point, LocalDate from,
                         LocalDate to, int rainThreshold, int windThreshold, List<Map<String, Object>> covers) {
    }

    private record Found(Watch watch, List<WeatherBreach> breaches) {
    }
}
