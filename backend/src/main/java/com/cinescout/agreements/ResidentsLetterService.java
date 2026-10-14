package com.cinescout.agreements;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.domain.Location;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.domain.SceneRequirements;
import com.cinescout.dto.ResidentsLetterRequest;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.UserRepository;
import com.cinescout.service.ProjectAccess;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/**
 * The letter to a venue's neighbours (a "letter drop"), made on request from the scene's dates, times and crew and what
 * the user adds. Nothing is stored: the user prints it, or makes it again after a change.
 */
@Service
public class ResidentsLetterService {

    /** Wrapping after this counts as night work worth warning neighbours about. */
    private static final LocalTime LATE = LocalTime.of(22, 0);

    private final UserRepository users;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public ResidentsLetterService(UserRepository users, ProjectAccess access, BlockingTransactions db) {
        this.users = users;
        this.access = access;
        this.db = db;
    }

    public Mono<LetterFile> letter(UUID userId, UUID locationId, ResidentsLetterRequest request) {
        ResidentsLetterRequest given = request == null ? new ResidentsLetterRequest(null, null, null, null, null, null) : request;
        return db.call(() -> facts(userId, locationId, given))
                .flatMap(facts -> Mono.fromCallable(() -> new LetterFile(filename(facts.venueName()), ResidentsLetterPdf.write(facts)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    public record LetterFile(String filename, byte[] content) {
    }

    private ResidentsLetterFacts facts(UUID userId, UUID locationId, ResidentsLetterRequest given) {
        Location location = access.location(userId, locationId, ProjectRole.EDITOR);
        Scene scene = location.getScene();
        SceneRequirements needs = scene.requirements();
        String contactName = blank(given.contactName()) ? users.getReferenceById(userId).getDisplayName() : given.contactName();
        return new ResidentsLetterFacts(
                scene.getProject().getTitle(),
                given.productionCompany(),
                location.getName(),
                location.getAddress(),
                scene.getShootDateStart(),
                scene.getShootDateEnd(),
                scene.getCallTime(),
                scene.getWrapTime(),
                needs == null ? null : needs.estimatedCastAndCrewSize(),
                night(needs == null ? null : needs.timeOfDay(), scene.getCallTime(), scene.getWrapTime()),
                contactName,
                given.contactPhone(),
                given.contactEmail(),
                given.parking(),
                given.note(),
                LocalDate.ofInstant(DatabaseTime.now(), ZoneOffset.UTC));
    }

    /** A night scene, a wrap after 22:00, or one past midnight. */
    static boolean night(String timeOfDay, LocalTime call, LocalTime wrap) {
        if (timeOfDay != null && timeOfDay.toLowerCase(Locale.ROOT).contains("night")) {
            return true;
        }
        if (wrap == null) {
            return false;
        }
        return wrap.isAfter(LATE) || (call != null && !wrap.isAfter(call));
    }

    /** "letter-to-neighbours-starlite-diner.pdf". */
    static String filename(String venue) {
        String slug = venue == null ? "" : venue.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-$", "");
        }
        return "letter-to-neighbours-" + (slug.isEmpty() ? "venue" : slug) + ".pdf";
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
