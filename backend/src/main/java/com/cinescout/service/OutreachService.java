package com.cinescout.service;

import com.cinescout.domain.OutreachDraft;
import com.cinescout.dto.OutreachDraftResponse;
import com.cinescout.dto.UpdateOutreachRequest;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.OutreachDraftRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * The user's own work on outreach drafts: reading, editing, marking as sent or replied, deleting. The
 * drafts themselves are written by the model in {@code OutreachGenerationService}. Every operation is
 * scoped to {@code ownerId}; entities never leave this class.
 */
@Service
public class OutreachService {

    private final OutreachDraftRepository drafts;
    private final LocationRepository locations;
    private final BlockingTransactions db;

    public OutreachService(OutreachDraftRepository drafts, LocationRepository locations, BlockingTransactions db) {
        this.drafts = drafts;
        this.locations = locations;
        this.db = db;
    }

    /** Newest first. */
    public Mono<List<OutreachDraftResponse>> list(UUID ownerId, UUID locationId) {
        return db.call(() -> {
            locations.findOwned(locationId, ownerId).orElseThrow(() -> new NotFoundException("Location", locationId));
            return drafts.findOwnedByLocation(locationId, ownerId).stream().map(OutreachDraftResponse::from).toList();
        });
    }

    public Mono<OutreachDraftResponse> get(UUID ownerId, UUID draftId) {
        return db.call(() -> OutreachDraftResponse.from(owned(ownerId, draftId)));
    }

    /**
     * Full replacement of the editable fields: a null recipient name or email clears it. Moving the draft
     * out of {@code DRAFT} stamps {@code sentAt}, moving it back clears it, see {@link OutreachDraft#changeStatus}.
     */
    public Mono<OutreachDraftResponse> update(UUID ownerId, UUID draftId, UpdateOutreachRequest request) {
        return db.call(() -> {
            OutreachDraft draft = owned(ownerId, draftId);
            draft.setSubject(request.subject().strip());
            draft.setBody(request.body().strip());
            draft.setTone(request.tone());
            draft.setRecipientName(blankToNull(request.recipientName()));
            draft.setRecipientEmail(blankToNull(request.recipientEmail()));
            draft.changeStatus(request.status());
            return OutreachDraftResponse.from(drafts.saveAndFlush(draft));
        });
    }

    public Mono<Void> delete(UUID ownerId, UUID draftId) {
        return db.run(() -> drafts.delete(owned(ownerId, draftId)));
    }

    private OutreachDraft owned(UUID ownerId, UUID draftId) {
        return drafts.findOwned(draftId, ownerId).orElseThrow(() -> new NotFoundException("Outreach draft", draftId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
