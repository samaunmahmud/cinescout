package com.cinescout.pack;

import com.cinescout.agreements.AgreementPdf;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.RecceEntry;
import com.cinescout.domain.Scene;
import com.cinescout.domain.VenueAvailability;
import com.cinescout.files.FileStore;
import com.cinescout.permits.FilmingOffices;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationPhotoRepository;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.SceneRepository;
import com.cinescout.repository.UserRepository;
import com.cinescout.repository.VenueAvailabilityRepository;
import com.cinescout.service.ProjectAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gathers a production's location pack (each scene's confirmed venue, or its best shortlisted ones) and writes it as a
 * PDF, with the venues' cover photos. Anyone on the project may download it.
 */
@Service
public class LocationPackService {

    private static final Logger log = LoggerFactory.getLogger(LocationPackService.class);
    /** While a scene has no confirmed venue, how many of its shortlist the pack shows. */
    static final int SHORTLIST_PER_SCENE = 3;
    private static final int MAX_SCENES = 300;
    private static final int MAX_VENUES = 600;
    private static final int PHOTO_FETCHES = 4;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final Map<String, String> RECCE_LABELS = new LinkedHashMap<>();

    static {
        RECCE_LABELS.put("sockets", "Sockets");
        RECCE_LABELS.put("threePhase", "Three-phase power");
        RECCE_LABELS.put("powerNotes", "Power notes");
        RECCE_LABELS.put("ceilingHeightM", "Ceiling height (m)");
        RECCE_LABELS.put("loadInRoute", "Load-in route");
        RECCE_LABELS.put("stairsOrLift", "Stairs or lift");
        RECCE_LABELS.put("stepFree", "Step-free");
        RECCE_LABELS.put("ambientNoise", "Ambient noise (1-5)");
        RECCE_LABELS.put("phoneSignal", "Phone signal");
        RECCE_LABELS.put("toilets", "Toilets");
        RECCE_LABELS.put("holdingSpace", "Holding space");
        RECCE_LABELS.put("notes", "Recce notes");
    }

    /** The PDF and the file name to save it as. */
    public record Pack(byte[] content, String filename) {
    }

    private final SceneRepository scenes;
    private final LocationRepository locations;
    private final VenueAvailabilityRepository availability;
    private final LocationPhotoRepository photos;
    private final UserRepository users;
    private final FilmingOffices offices;
    private final FileStore files;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public LocationPackService(SceneRepository scenes, LocationRepository locations, VenueAvailabilityRepository availability,
                               LocationPhotoRepository photos, UserRepository users, FilmingOffices offices, FileStore files, ProjectAccess access,
                               BlockingTransactions db) {
        this.scenes = scenes;
        this.locations = locations;
        this.availability = availability;
        this.photos = photos;
        this.users = users;
        this.offices = offices;
        this.files = files;
        this.access = access;
        this.db = db;
    }

    public Mono<Pack> pack(UUID userId, UUID projectId) {
        return db.call(() -> gather(userId, projectId))
                .flatMap(gathered -> withPhotos(gathered.facts(), gathered.photoKeys()))
                .flatMap(facts -> Mono.fromCallable(() -> new Pack(LocationPackPdf.write(facts), filename(facts.production())))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private record Gathered(PackFacts facts, Map<String, String> photoKeys) {
    }

    private Gathered gather(UUID userId, UUID projectId) {
        Project project = access.project(userId, projectId, ProjectRole.VIEWER);
        List<Scene> sceneList = scenes.findVisibleByProject(projectId, userId, PageRequest.of(0, MAX_SCENES)).getContent();
        Map<UUID, List<Location>> venuesByScene = locations.findProjectShortlist(projectId,
                        List.of(LocationStatus.CONFIRMED, LocationStatus.CONTACTED, LocationStatus.SHORTLISTED), PageRequest.of(0, MAX_VENUES))
                .stream().collect(Collectors.groupingBy(venue -> venue.getScene().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<VenueAvailability>> daysByVenue = availability.findByProject(projectId).stream()
                .collect(Collectors.groupingBy(day -> day.getLocation().getId()));
        Map<String, String> photoKeys = new LinkedHashMap<>();
        List<PackFacts.Scene> packScenes = new ArrayList<>();
        for (Scene scene : sceneList) {
            List<Location> candidates = venuesByScene.getOrDefault(scene.getId(), List.of());
            List<Location> confirmed = candidates.stream().filter(venue -> venue.getStatus() == LocationStatus.CONFIRMED).toList();
            List<Location> shown = !confirmed.isEmpty() ? confirmed : candidates.stream()
                    .sorted(Comparator.comparing((Location venue) -> venue.getFitScore() == null ? -1 : venue.getFitScore()).reversed())
                    .limit(SHORTLIST_PER_SCENE)
                    .toList();
            List<PackFacts.Venue> venues = new ArrayList<>();
            for (Location venue : shown) {
                String place = packScenes.size() + ":" + venues.size();
                venues.add(venue(venue, scene, daysByVenue.getOrDefault(venue.getId(), List.of())));
                if (venue.getCoverPhotoId() != null) {
                    photos.findById(venue.getCoverPhotoId()).ifPresent(photo -> photoKeys.put(place, photo.getStorageKey()));
                }
            }
            packScenes.add(new PackFacts.Scene(label(scene), AgreementPdf.dates(scene.getShootDateStart(), scene.getShootDateEnd()),
                    access(scene), venues));
        }
        String preparedBy = users.findById(userId).map(user -> user.getDisplayName()).orElse(null);
        return new Gathered(new PackFacts(project.getTitle(), project.getLocationArea(), preparedBy, LocalDate.now(), packScenes), photoKeys);
    }

    /**
     * The facts with each venue's cover photo filled in, {@code keys} naming the stored photo by the venue's place in the
     * pack ("scene:venue"). A photo that cannot be read is left out, not the pack.
     */
    private Mono<PackFacts> withPhotos(PackFacts facts, Map<String, String> keys) {
        return Flux.fromIterable(keys.entrySet())
                .flatMap(entry -> files.get(entry.getValue())
                        .map(bytes -> Map.entry(entry.getKey(), bytes))
                        .onErrorResume(error -> {
                            log.warn("A cover photo could not be read for the location pack: {}", error.toString());
                            return Mono.empty();
                        }), PHOTO_FETCHES)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(bytes -> {
                    List<PackFacts.Scene> scenesWithPhotos = new ArrayList<>();
                    for (int i = 0; i < facts.scenes().size(); i++) {
                        PackFacts.Scene scene = facts.scenes().get(i);
                        List<PackFacts.Venue> venues = new ArrayList<>();
                        for (int j = 0; j < scene.venues().size(); j++) {
                            venues.add(scene.venues().get(j).withPhoto(bytes.get(i + ":" + j)));
                        }
                        scenesWithPhotos.add(new PackFacts.Scene(scene.label(), scene.dates(), scene.access(), venues));
                    }
                    return new PackFacts(facts.production(), facts.area(), facts.preparedBy(), facts.preparedOn(), scenesWithPhotos);
                });
    }

    private PackFacts.Venue venue(Location venue, Scene scene, List<VenueAvailability> days) {
        boolean placed = venue.getLatitude() != null && venue.getLongitude() != null;
        String position = placed ? venue.getLatitude().stripTrailingZeros().toPlainString() + ", " + venue.getLongitude().stripTrailingZeros().toPlainString() : null;
        // Short enough for one line; OpenStreetMap centres on the marker.
        String mapUrl = placed ? "https://www.openstreetmap.org/?mlat=" + venue.getLatitude().stripTrailingZeros().toPlainString() + "&mlon="
                + venue.getLongitude().stripTrailingZeros().toPlainString() : null;
        String contact = joined(", ", venue.getContactName(), venue.getContactEmail(), venue.getContactPhone());
        List<String> dayLines = days.stream()
                .sorted(Comparator.comparing(VenueAvailability::getDay))
                .map(day -> DAY.format(day.getDay()) + ": " + day.getState().name().toLowerCase(Locale.ROOT)
                        + (day.getNote() == null || day.getNote().isBlank() ? "" : " (" + day.getNote().strip() + ")"))
                .toList();
        List<String[]> recce = new ArrayList<>();
        Map<String, RecceEntry> answers = venue.getRecce();
        RECCE_LABELS.forEach((field, label) -> {
            RecceEntry entry = answers.get(field);
            if (entry != null && entry.value() != null) {
                recce.add(new String[] {label, answer(entry.value())});
            }
        });
        return new PackFacts.Venue(venue.getName(), status(venue.getStatus()), venue.getAddress(), position, mapUrl,
                venue.getFitScore() == null ? null : venue.getFitScore().intValue(), venue.getFitReason(),
                venue.getBookingFriction() == null ? null : booking(venue.getBookingFriction().name()), venue.getFrictionNote(),
                venue.getFootprintWarnings(), contact, venue.getQuote(), venue.getNotes(), venue.getSourceUrl(), dayLines, recce,
                permit(venue, scene), null);
    }

    private String permit(Location venue, Scene scene) {
        if (venue.getAdminArea() == null) {
            return null;
        }
        return offices.officeFor(venue.getAdminArea())
                .map(office -> {
                    Integer lead = office.leadTimeFor(scene.requirements() == null ? null : scene.requirements().estimatedCastAndCrewSize());
                    return office.office() + (lead == null ? "" : ", apply " + lead + " working days ahead") + " — " + office.contactUrl();
                })
                .orElse(null);
    }

    private static String answer(Object value) {
        if (value instanceof Boolean yes) {
            return yes ? "Yes" : "No";
        }
        String text = String.valueOf(value);
        return text.matches("[A-Z_]+") ? text.charAt(0) + text.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ') : text;
    }

    private static String status(LocationStatus status) {
        return switch (status) {
            case CONFIRMED -> "Confirmed";
            case CONTACTED -> "Contacted";
            case SHORTLISTED -> "Shortlisted";
            case SUGGESTED -> "Suggested";
            case REJECTED -> "Passed on";
        };
    }

    private static String booking(String friction) {
        return switch (friction) {
            case "PUBLIC" -> "Public space: a filming permit";
            case "COMMERCIAL" -> "Commercial venue: book with the business";
            case "PRIVATE" -> "Private property: the owner's permission";
            default -> null;
        };
    }

    private static String label(Scene scene) {
        return (scene.getSceneNumber() == null ? "" : scene.getSceneNumber() + ". ") + scene.getTitle();
    }

    private static String access(Scene scene) {
        String times = AgreementPdf.access(scene.getCallTime(), scene.getWrapTime());
        return times == null ? null : "Call to wrap " + times;
    }

    private static String joined(String separator, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                kept.add(part.strip());
            }
        }
        return kept.isEmpty() ? null : String.join(separator, kept);
    }

    /** "The Night Ferry location pack.pdf", with what a file name cannot hold taken out. */
    static String filename(String production) {
        String name = production.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").strip();
        return (name.isEmpty() ? "Location pack" : name + " location pack") + ".pdf";
    }
}
