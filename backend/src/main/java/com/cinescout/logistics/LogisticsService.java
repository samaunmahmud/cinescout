package com.cinescout.logistics;

import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.domain.Location;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.logistics.LogisticsException.Kind;
import com.cinescout.logistics.LogisticsReport.Environment;
import com.cinescout.logistics.LogisticsReport.Position;
import com.cinescout.logistics.LogisticsReport.SectionStatus;
import com.cinescout.logistics.LogisticsReport.ShootWindow;
import com.cinescout.logistics.LogisticsReport.Solar;
import com.cinescout.logistics.LogisticsReport.Weather;
import com.cinescout.logistics.LogisticsReport.WeatherDay;
import com.cinescout.logistics.geocoding.Geocoder;
import com.cinescout.logistics.places.PlacesClient;
import com.cinescout.logistics.solar.SceneLight;
import com.cinescout.logistics.solar.SolarCalculator;
import com.cinescout.logistics.solar.SolarDay;
import com.cinescout.logistics.weather.DailyWeather;
import com.cinescout.logistics.weather.WeatherClient;
import com.cinescout.logistics.weather.WeatherPlan;
import com.cinescout.logistics.weather.WeatherPlan.Basis;
import com.cinescout.logistics.weather.WeatherPlan.Entry;
import com.cinescout.logistics.weather.WeatherPlan.Range;
import com.cinescout.logistics.weather.WeatherSeries;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.resilience.Guard;
import com.cinescout.resilience.GuardFactory;
import com.cinescout.service.ConflictException;
import com.cinescout.service.NotFoundException;
import com.cinescout.service.ProjectAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Works out a location's logistics (module B) and caches them on it: the light on each shoot day, the
 * weather, the noise risk and the services nearby.
 *
 * <p>A venue without coordinates is first geocoded from its address (or its name and the project's area),
 * and the coordinates found are kept. Weather and places are then fetched at the same time, each behind its
 * own guard; either may fail without failing the report, which then says what is missing. The solar times
 * are computed locally, in the time zone the weather provider resolves. As elsewhere, database work runs in
 * short transactions that never span a provider call, and ownership is re-checked before saving.
 */
public class LogisticsService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsService.class);

    /** A scene without shoot dates is shown the coming week. */
    static final int ASSUMED_DAYS = 7;

    private final WeatherClient weather;
    private final PlacesClient places;
    private final Geocoder geocoder;
    private final Guard weatherGuard;
    private final Guard placesGuard;
    private final Guard geocodingGuard;
    private final LocationRepository locations;
    private final ProjectAccess access;
    private final BlockingTransactions db;
    private final ObjectMapper mapper;
    private final LogisticsProperties props;
    private final Clock clock;

    public LogisticsService(WeatherClient weather, PlacesClient places, Geocoder geocoder, GuardFactory guards,
                            LocationRepository locations, ProjectAccess access, BlockingTransactions db, ObjectMapper mapper,
                            LogisticsProperties props, Clock clock) {
        this.weather = weather;
        this.places = places;
        this.geocoder = geocoder;
        this.weatherGuard = guard(guards, "weather", true);
        // The public Overpass server answers a busy spell with 504s and timeouts that take half a minute each;
        // retrying would hold the request for minutes and add to its load. One try, then the section is marked
        // unavailable and the user can refresh later. (A query the server turns away at once is tried a second
        // time by the client itself, which costs seconds, not minutes.)
        this.placesGuard = guard(guards, "places", false);
        this.geocodingGuard = guard(guards, "geocoding", true);
        this.locations = locations;
        this.access = access;
        this.db = db;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
    }

    /** Only outages trip a breaker; a request the provider rejects is our problem, not its. */
    private static Guard guard(GuardFactory guards, String name, boolean retry) {
        return guards.create(name,
                error -> retry && error instanceof LogisticsException e && e.isRetryable(),
                error -> error instanceof LogisticsException e && e.kind() != Kind.INVALID_REQUEST,
                open -> new LogisticsException(Kind.UNAVAILABLE, "The " + name + " circuit breaker is open; the call was not made", open));
    }

    /**
     * Works the logistics out afresh, caches them on the location and returns them.
     *
     * @throws NotFoundException  (as an error signal) if the location is not the owner's
     * @throws ConflictException  (as an error signal) if the venue has no coordinates and cannot be found on the map
     * @throws LogisticsException (as an error signal) if the venue has to be geocoded and the geocoder is down
     */
    public Mono<LogisticsReport> refresh(UUID userId, UUID locationId) {
        return db.call(() -> brief(access.location(userId, locationId, ProjectRole.EDITOR)))
                .flatMap(brief -> locate(brief, locationId))
                .flatMap(this::report)
                .flatMap(report -> db.call(() -> save(userId, locationId, report)));
    }

    /**
     * The logistics cached on the location, exactly as they were returned when worked out.
     *
     * @throws NotFoundException (as an error signal) if the location is not the owner's or has none yet
     */
    public Mono<JsonNode> cached(UUID userId, UUID locationId) {
        return db.call(() -> {
            JsonNode logistics = access.location(userId, locationId, ProjectRole.VIEWER).getLogisticsJson();
            if (logistics == null) {
                throw new NotFoundException("Logistics for location", locationId);
            }
            return logistics;
        });
    }

    // --- 1. what the report needs from the database ----------------------------------------------------

    /**
     * @param point        the stored coordinates, or null
     * @param geocodeQuery what to look the venue up by if it has no coordinates
     * @param byAddress    whether that query is the venue's address (rather than its name)
     * @param venueName    the location's name, to tell the venue itself apart from the places around it
     */
    /** @param knownZone the venue's time zone from its last report, for when this one's weather does not say; null if none */
    record Brief(GeoPoint point, String geocodeQuery, boolean byAddress, String venueName, LocalDate shootStart, LocalDate shootEnd,
                 String timeOfDay, AcousticSensitivity sensitivity, LocalTime callTime, LocalTime wrapTime, ZoneId knownZone) {
    }

    /**
     * The time zone a cached report found for the venue; null when there is none or it was the UTC stand-in. A moved pin
     * drops the cached report, so the zone is always the venue's own.
     */
    static ZoneId knownZone(JsonNode report) {
        String zone = report == null ? null : report.path("timeZone").asText(null);
        if (zone == null || zone.isBlank() || zone.equals("UTC")) {
            return null;
        }
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private Brief brief(Location location) {
        Scene scene = location.getScene();
        SceneRequirements requirements = scene.requirements();
        GeoPoint point = location.getLatitude() == null || location.getLongitude() == null ? null
                : new GeoPoint(location.getLatitude().doubleValue(), location.getLongitude().doubleValue());
        boolean byAddress = location.getAddress() != null && !location.getAddress().isBlank();
        return new Brief(point, geocodeQuery(location, byAddress, scene.getProject().getLocationArea()), byAddress,
                location.getName(),
                scene.getShootDateStart(), scene.getShootDateEnd(),
                requirements == null ? null : requirements.timeOfDay(),
                requirements == null ? null : requirements.acousticSensitivity(),
                scene.getCallTime(), scene.getWrapTime(), knownZone(location.getLogisticsJson()));
    }

    /** The venue's address; failing that its name, in the project's area so a common name is found in the right city. */
    private static String geocodeQuery(Location location, boolean byAddress, String area) {
        if (byAddress) {
            return location.getAddress();
        }
        return area == null || area.isBlank() ? location.getName() : location.getName() + ", " + area;
    }

    // --- 2. where the venue is ------------------------------------------------------------------------

    record Located(Brief brief, GeoPoint point, boolean geocoded) {
    }

    private Mono<Located> locate(Brief brief, UUID locationId) {
        if (brief.point() != null) {
            return Mono.just(new Located(brief, brief.point(), false));
        }
        return geocodingGuard.call(() -> geocoder.locate(brief.geocodeQuery()))
                .map(point -> new Located(brief, point, true))
                .switchIfEmpty(Mono.error(() -> new ConflictException("Location " + locationId
                        + " could not be found on the map from its address or name; set its coordinates and try again")));
    }

    // --- 3. the report ---------------------------------------------------------------------------------

    private Mono<LogisticsReport> report(Located located) {
        Brief brief = located.brief();
        LocalDate today = LocalDate.now(clock);
        ShootWindow window = window(brief.shootStart(), brief.shootEnd(), today, props.maxDays());
        List<LocalDate> dates = window.start().datesUntil(window.end().plusDays(1)).toList();
        WeatherPlan plan = WeatherPlan.of(dates, today, weather.forecastDaysAhead(), weather.forecastDaysBack());
        SceneLight light = SceneLight.fromTimeOfDay(brief.timeOfDay());

        Mono<Fetch> forecast = fetch(plan.forecastRange(), range -> weather.forecast(located.point(), range.from(), range.to()));
        Mono<Fetch> history = fetch(plan.historyRange(), range -> weather.history(located.point(), range.from(), range.to()));
        int unitBaseRadius = places.unitBaseRadiusMeters();
        Mono<Surroundings> surroundings = placesGuard.call(() -> places.around(located.point()))
                .map(found -> new Surroundings(EnvironmentAssessor.assess(found, brief.sensitivity(), brief.venueName()),
                        UnitBaseFinder.find(found, unitBaseRadius)))
                .onErrorResume(LogisticsException.class, e -> {
                    log.warn("Places lookup failed ({}): {}", e.kind(), e.getMessage());
                    String message = "The map service could not be reached; try again later";
                    return Mono.just(new Surroundings(EnvironmentAssessor.unavailable(message, brief.sensitivity()),
                            UnitBaseFinder.unavailable(message, unitBaseRadius)));
                });

        return Mono.zip(forecast, history, surroundings)
                .map(results -> assemble(located, window, plan, light, results.getT1(), results.getT2(),
                        results.getT3().environment(), results.getT3().unitBase()));
    }

    /** What the one map lookup around the venue gives: its environment and its unit base. */
    private record Surroundings(Environment environment, LogisticsReport.UnitBase unitBase) {
    }

    static ShootWindow window(LocalDate start, LocalDate end, LocalDate today, int maxDays) {
        boolean assumed = start == null && end == null;
        LocalDate first = assumed ? today : start != null ? start : end;
        LocalDate last = assumed ? today.plusDays(ASSUMED_DAYS - 1) : end != null ? end : start;
        boolean truncated = ChronoUnit.DAYS.between(first, last) + 1 > maxDays;
        return new ShootWindow(first, truncated ? first.plusDays(maxDays - 1) : last, assumed, truncated);
    }

    /** A weather call's outcome: the series, or null if it was not needed ({@code failed} false) or failed. */
    record Fetch(WeatherSeries series, boolean failed) {
        static final Fetch NOT_NEEDED = new Fetch(null, false);
        static final Fetch FAILED = new Fetch(null, true);
    }

    private Mono<Fetch> fetch(Optional<Range> range, Function<Range, Mono<WeatherSeries>> call) {
        if (range.isEmpty()) {
            return Mono.just(Fetch.NOT_NEEDED);
        }
        return weatherGuard.call(() -> call.apply(range.get()))
                .map(series -> new Fetch(series, false))
                .onErrorResume(LogisticsException.class, e -> {
                    log.warn("Weather lookup failed ({}): {}", e.kind(), e.getMessage());
                    return Mono.just(Fetch.FAILED);
                });
    }

    private LogisticsReport assemble(Located located, ShootWindow window, WeatherPlan plan, SceneLight light,
                                     Fetch forecast, Fetch history, Environment environment, LogisticsReport.UnitBase unitBase) {
        Brief brief = located.brief();
        List<String> notes = new ArrayList<>();
        Set<String> attribution = new LinkedHashSet<>();

        ZoneId zone = forecast.series() != null ? forecast.series().zone()
                : history.series() != null ? history.series().zone() : null;
        if (zone == null) {
            zone = brief.knownZone();
        }
        if (zone == null) {
            zone = ZoneId.of("UTC");
            notes.add("Times are in UTC: the location's time zone could not be looked up.");
        }
        if (located.geocoded()) {
            notes.add("The coordinates were looked up from the venue's " + (brief.byAddress() ? "address" : "name")
                    + "; check the pin on a map and correct it if it is wrong.");
            attribution.add(geocoder.attribution());
        }
        if (window.assumed()) {
            notes.add("The scene has no shoot dates, so the coming week is shown.");
        }
        if (window.truncated()) {
            notes.add("Only the first " + props.maxDays() + " days of the shoot window are covered.");
        }

        Weather weatherSection = weatherSection(plan, forecast, history, light, brief.sensitivity());
        if (weatherSection.days().stream().anyMatch(day -> day.basis() == Basis.PAST_YEAR)) {
            notes.add("Days more than " + weather.forecastDaysAhead()
                    + " days ahead show the weather recorded on the same date in an earlier year, not a forecast.");
        }
        if (weatherSection.status() != SectionStatus.UNAVAILABLE) {
            for (Fetch fetch : List.of(forecast, history)) {
                if (fetch.series() != null) {
                    attribution.add(fetch.series().attribution() != null ? fetch.series().attribution() : weather.attribution());
                }
            }
            if (forecast.series() == null && history.series() == null) {
                attribution.add(weather.attribution());
            }
        }
        if (environment.status() != SectionStatus.UNAVAILABLE) {
            attribution.add(places.attribution());
        }

        ZoneId localZone = zone;
        List<SolarDay> solarDays = plan.entries().stream()
                .map(entry -> SolarCalculator.day(entry.date(), located.point(), localZone, light, brief.callTime(), brief.wrapTime()))
                .toList();

        return new LogisticsReport(LogisticsReport.VERSION, clock.instant(),
                new Position(degrees(located.point().latitude()), degrees(located.point().longitude()), located.geocoded()),
                zone.getId(), window,
                new Solar(brief.timeOfDay(), light, solarDays),
                weatherSection, environment, unitBase, List.copyOf(notes), List.copyOf(attribution));
    }

    private static Weather weatherSection(WeatherPlan plan, Fetch forecast, Fetch history, SceneLight light,
                                          AcousticSensitivity sensitivity) {
        List<WeatherDay> days = new ArrayList<>();
        for (Entry entry : plan.entries()) {
            WeatherSeries series = entry.fromForecast() ? forecast.series() : history.series();
            DailyWeather day = series == null ? null : series.days().stream()
                    .filter(d -> entry.referenceDate().equals(d.date())).findFirst().orElse(null);
            if (day != null) {
                days.add(new WeatherDay(entry.date(), entry.basis(), entry.referenceDate(),
                        WeatherAdvisor.summary(day.weatherCode()),
                        day.temperatureMaxC(), day.temperatureMinC(), day.precipitationMm(),
                        day.precipitationProbabilityPercent(), day.windSpeedMaxKmh(), day.windGustsMaxKmh(),
                        day.cloudCoverPercent(), WeatherAdvisor.warnings(day, light, sensitivity)));
            }
        }
        int missing = plan.entries().size() - days.size();
        if (missing == 0) {
            return new Weather(SectionStatus.OK, null, List.copyOf(days));
        }
        boolean failed = forecast.failed() || history.failed();
        if (days.isEmpty()) {
            return new Weather(SectionStatus.UNAVAILABLE, failed
                    ? "The weather service could not be reached; try again later"
                    : "The weather service has no data for these dates", List.of());
        }
        return new Weather(SectionStatus.PARTIAL, "Weather for " + missing + " of " + plan.entries().size()
                + " days could not be looked up", List.copyOf(days));
    }

    private static BigDecimal degrees(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    // --- 4. saving ---------------------------------------------------------------------------------------

    /**
     * Caches the report, and keeps coordinates that were geocoded. If the location's coordinates changed
     * while the report was being worked out, the report is for the old spot: it is returned but not cached.
     */
    private LogisticsReport save(UUID userId, UUID locationId, LogisticsReport report) {
        Location location = access.location(userId, locationId, ProjectRole.EDITOR);
        BigDecimal latitude = report.position().latitude();
        BigDecimal longitude = report.position().longitude();
        if (location.getLatitude() == null && location.getLongitude() == null && report.position().geocoded()) {
            location.setLatitude(latitude);
            location.setLongitude(longitude);
        } else if (!sameCoordinate(location.getLatitude(), latitude) || !sameCoordinate(location.getLongitude(), longitude)) {
            log.debug("Location {} moved while its logistics were worked out; not caching them", locationId);
            return report;
        }
        location.cacheLogistics(mapper.valueToTree(report));
        locations.saveAndFlush(location);
        return report;
    }

    private static boolean sameCoordinate(BigDecimal stored, BigDecimal used) {
        return stored != null && stored.compareTo(used) == 0;
    }

}
