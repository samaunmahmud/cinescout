package com.cinescout.service;

import com.cinescout.domain.AvailabilityState;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.ProjectRole;
import com.cinescout.dto.AvailabilityRequest;
import com.cinescout.dto.GenerateOutreachRequest;
import com.cinescout.dto.OutreachDraftResponse;
import com.cinescout.dto.PlanBRequest;
import com.cinescout.dto.PlanBResponse;
import com.cinescout.llm.LlmException;
import com.cinescout.outreach.OutreachGenerationService;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.ratelimit.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Weather plan B: when rain or wind threatens a shoot day, pencil a backup venue for that day and have the AI draft
 * the email asking its owner to hold it. The pencil is made whatever happens to the email.
 */
@Service
public class PlanBService {

    private static final Logger log = LoggerFactory.getLogger(PlanBService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK);

    private final AvailabilityService availability;
    private final ObjectProvider<OutreachGenerationService> generation;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public PlanBService(AvailabilityService availability, ObjectProvider<OutreachGenerationService> generation, ProjectAccess access,
                        BlockingTransactions db) {
        this.availability = availability;
        this.generation = generation;
        this.access = access;
        this.db = db;
    }

    /** @param permit the caller's AI allowance, taken just before the email is written */
    public Mono<PlanBResponse> ask(UUID userId, UUID locationId, PlanBRequest request, Supplier<Mono<Void>> permit) {
        String reason = request.reason() == null || request.reason().isBlank() ? "the weather forecast" : request.reason().strip();
        return db.call(() -> {
                    Location venue = access.location(userId, locationId, ProjectRole.EDITOR);
                    if (venue.getStatus() == LocationStatus.REJECTED || venue.getStatus() == LocationStatus.CONFIRMED) {
                        throw new ConflictException(venue.getName() + (venue.getStatus() == LocationStatus.CONFIRMED
                                ? " is already the confirmed venue; plan B is another of the scene's venues"
                                : " was passed on; pick another of the scene's venues as plan B"));
                    }
                    return venue.getScene().getTitle();
                })
                .flatMap(scene -> availability.set(userId, locationId, new AvailabilityRequest(request.day(), request.day(), AvailabilityState.PENCILLED,
                                null, "Weather plan B for " + scene + ": " + reason))
                        .flatMap(days -> draft(userId, locationId, request, reason, scene, permit)
                                .map(draft -> new PlanBResponse(days, draft.orElse(null), null))
                                .onErrorResume(error -> Mono.just(new PlanBResponse(days, null, problem(error))))));
    }

    private Mono<Optional<OutreachDraftResponse>> draft(UUID userId, UUID locationId, PlanBRequest request, String reason, String scene,
                                                        Supplier<Mono<Void>> permit) {
        OutreachGenerationService service = generation.getIfAvailable();
        if (service == null) {
            return Mono.error(new FeatureUnavailableReason());
        }
        String context = "This is a request to hold the venue as a weather backup, not a firm booking. Our shoot for the scene \""
                + scene + "\" on " + request.day().format(DAY) + " may have to move indoors or elsewhere at short notice because of "
                + reason + ". Ask whether the venue could be held for us on that day as a backup, what notice they would need, and "
                + "on what terms (a fee to hold it, and the fee if we do use it). Keep it short and friendly.";
        return permit.get()
                .then(Mono.defer(() -> service.generate(userId, locationId, new GenerateOutreachRequest(null, null, null, context))))
                .map(Optional::of);
    }

    private static String problem(Throwable error) {
        if (error instanceof FeatureUnavailableReason) {
            return "AI emails are not set up on this server; write to the venue from its Outreach tab.";
        }
        if (error instanceof RateLimitExceededException) {
            return "Your hourly allowance of AI calls is used up; draft the email from the venue's Outreach tab later.";
        }
        if (error instanceof LlmException) {
            return "The AI could not write the email just now; try again from the venue's Outreach tab.";
        }
        log.warn("Plan B email could not be drafted", error);
        return "The email could not be drafted; write it from the venue's Outreach tab.";
    }

    /** Marks "no generator configured" without an exception type of its own in the API. */
    private static final class FeatureUnavailableReason extends RuntimeException {
        FeatureUnavailableReason() {
            super("outreach generation is not configured", null, false, false);
        }
    }
}
