package com.cinescout.outreach;

import com.cinescout.domain.Location;
import com.cinescout.domain.OutreachDraft;
import com.cinescout.domain.OutreachStatus;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.dto.GenerateOutreachRequest;
import com.cinescout.dto.OutreachDraftResponse;
import com.cinescout.llm.LlmClient;
import com.cinescout.llm.LlmGuards;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.OutreachDraftRepository;
import com.cinescout.repository.UserRepository;
import com.cinescout.resilience.Guard;
import com.cinescout.resilience.GuardFactory;
import com.cinescout.service.ConflictException;
import com.cinescout.service.NotFoundException;
import com.cinescout.service.ProjectAccess;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Has the model draft an email to the owner of a saved location and stores it as an outreach draft.
 *
 * <p>Like scouting, every database step runs on {@code boundedElastic} in its own short transaction and
 * never spans the model call. Access is checked when the context is read and again when the draft is
 * saved, so a location deleted mid-run fails as a 404 instead of being written to. The model call shares
 * the LLM circuit breaker with scouting, see {@link LlmGuards}.
 */
public class OutreachGenerationService {

    private static final int MAX_SUBJECT_CHARS = 300;

    private final LlmClient llm;
    private final Guard llmGuard;
    private final LocationRepository locations;
    private final OutreachDraftRepository drafts;
    private final UserRepository users;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public OutreachGenerationService(LlmClient llm, GuardFactory guards, LocationRepository locations,
                                     OutreachDraftRepository drafts, UserRepository users, ProjectAccess access, BlockingTransactions db) {
        this.llm = llm;
        this.llmGuard = LlmGuards.create(guards);
        this.locations = locations;
        this.drafts = drafts;
        this.users = users;
        this.access = access;
        this.db = db;
    }

    /**
     * Drafts an email and saves it as a {@code DRAFT}. Several drafts per location are fine; each call
     * writes a new one.
     *
     * @param request may be null, which means "all defaults"
     * @throws NotFoundException (as an error signal) if the location is not the owner's
     */
    public Mono<OutreachDraftResponse> generate(UUID userId, UUID locationId, GenerateOutreachRequest request) {
        GenerateOutreachRequest options = request == null ? new GenerateOutreachRequest(null, null, null, null) : request;
        return db.call(() -> brief(userId, locationId, options))
                .flatMap(brief -> llmGuard.call(() -> llm.generate(
                        OutreachPrompts.SYSTEM, OutreachPrompts.user(brief), OutreachEmail.class)))
                .flatMap(email -> db.call(() -> save(userId, locationId, options, email)));
    }

    /**
     * Drafts a short chaser for an email that was sent and has had no answer, and saves it as a new {@code DRAFT} that
     * points at it. The first email then no longer waits on a follow-up; the chaser, once sent, may in turn be flagged.
     * Its subject is "Re: " and the first one's, set here rather than by the model.
     *
     * @throws ConflictException (as an error signal) unless the email is marked as sent
     */
    public Mono<OutreachDraftResponse> followUp(UUID userId, UUID draftId) {
        return db.call(() -> followUpBrief(userId, draftId))
                .flatMap(brief -> llmGuard.call(() -> llm.generate(
                        OutreachPrompts.FOLLOW_UP_SYSTEM, OutreachPrompts.followUpUser(brief), OutreachEmail.class)))
                .flatMap(email -> db.call(() -> saveFollowUp(userId, draftId, email)));
    }

    private FollowUpBrief followUpBrief(UUID userId, UUID draftId) {
        OutreachDraft earlier = sentDraft(userId, draftId);
        Location location = earlier.getLocation();
        return new FollowUpBrief(
                users.getReferenceById(userId).getDisplayName(),
                location.getScene().getProject().getTitle(),
                location.getName(),
                earlier.getRecipientName(),
                earlier.getSubject(),
                earlier.getSentAt() == null ? null : LocalDate.ofInstant(earlier.getSentAt(), ZoneOffset.UTC),
                earlier.getTone());
    }

    private OutreachDraftResponse saveFollowUp(UUID userId, UUID draftId, OutreachEmail email) {
        OutreachDraft earlier = sentDraft(userId, draftId);
        OutreachDraft draft = new OutreachDraft(earlier.getLocation(), users.getReferenceById(userId),
                oneLine(reply(earlier.getSubject())), email.body().strip(), earlier.getTone());
        draft.setRecipientName(earlier.getRecipientName());
        draft.setRecipientEmail(earlier.getRecipientEmail());
        draft.setGeneratedBy(llm.modelId());
        draft.followUp(earlier);
        drafts.save(earlier);
        return OutreachDraftResponse.from(drafts.saveAndFlush(draft));
    }

    private OutreachDraft sentDraft(UUID userId, UUID draftId) {
        OutreachDraft draft = access.draft(userId, draftId, ProjectRole.EDITOR);
        if (draft.getStatus() != OutreachStatus.SENT) {
            throw new ConflictException(draft.getStatus() == OutreachStatus.REPLIED
                    ? "This email has had a reply, so it needs no follow-up"
                    : "Mark this email as sent before drafting a follow-up");
        }
        return draft;
    }

    private static String reply(String subject) {
        String stripped = subject.strip();
        return stripped.regionMatches(true, 0, "Re:", 0, 3) ? stripped : "Re: " + stripped;
    }

    private OutreachBrief brief(UUID userId, UUID locationId, GenerateOutreachRequest options) {
        Location location = access.location(userId, locationId, ProjectRole.EDITOR);
        Scene scene = location.getScene();
        // Signed by whoever on the crew writes it, not necessarily the project's owner.
        return new OutreachBrief(
                users.getReferenceById(userId).getDisplayName(),
                scene.getProject().getTitle(),
                scene.getShootDateStart(),
                scene.getShootDateEnd(),
                scene.requirements(),
                location.getName(),
                location.getAddress(),
                location.getBookingFriction(),
                location.getFrictionNote(),
                location.getSourceExcerpt(),
                blankToNull(options.recipientName()),
                options.tone(),
                blankToNull(options.additionalContext()));
    }

    private OutreachDraftResponse save(UUID userId, UUID locationId, GenerateOutreachRequest options, OutreachEmail email) {
        Location location = access.location(userId, locationId, ProjectRole.EDITOR);
        OutreachDraft draft = new OutreachDraft(location, users.getReferenceById(userId),
                oneLine(email.subject()), email.body().strip(), options.tone());
        draft.setRecipientName(blankToNull(options.recipientName()));
        draft.setRecipientEmail(blankToNull(options.recipientEmail()));
        draft.setGeneratedBy(llm.modelId());
        return OutreachDraftResponse.from(drafts.saveAndFlush(draft));
    }


    /** A subject is one line; a model that breaks it over several is tidied, not rejected. */
    private static String oneLine(String subject) {
        String collapsed = subject.strip().replaceAll("\\s+", " ");
        return collapsed.length() > MAX_SUBJECT_CHARS ? collapsed.substring(0, MAX_SUBJECT_CHARS) : collapsed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
