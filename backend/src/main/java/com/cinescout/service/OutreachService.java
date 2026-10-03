package com.cinescout.service;

import com.cinescout.domain.ActivityTarget;
import com.cinescout.domain.ActivityVerb;
import com.cinescout.domain.Location;
import com.cinescout.domain.OutreachDraft;
import com.cinescout.domain.OutreachStatus;
import com.cinescout.domain.ProjectRole;
import com.cinescout.dto.OutreachDraftResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.dto.ProjectOutreachResponse;
import com.cinescout.dto.UpdateOutreachRequest;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.OutreachDraftRepository;
import com.cinescout.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * The user's own work on outreach drafts: reading, editing, marking as sent or replied, deleting. The
 * drafts themselves are written by the model in {@code OutreachGenerationService}. Every operation is
 * scoped to {@code userId}; entities never leave this class.
 */
@Service
public class OutreachService {

    private final OutreachDraftRepository drafts;
    private final LocationRepository locations;
    private final ProjectRepository projects;
    private final ProjectAccess access;
    private final BlockingTransactions db;
    private final ActivityLog activity;

    public OutreachService(OutreachDraftRepository drafts, LocationRepository locations, ProjectRepository projects, ProjectAccess access,
                           BlockingTransactions db, ActivityLog activity) {
        this.drafts = drafts;
        this.locations = locations;
        this.projects = projects;
        this.access = access;
        this.db = db;
        this.activity = activity;
    }

    /**
     * Every draft of a project, newest first, each with its venue and scene; {@code status} null means all. With
     * {@code followUp}, only the emails waiting on a follow-up, oldest sent first (the status is then ignored).
     */
    public Mono<PageResponse<ProjectOutreachResponse>> listForProject(UUID userId, UUID projectId, OutreachStatus status, boolean followUp,
                                                                      PageQuery page) {
        return db.call(() -> {
            access.project(userId, projectId, ProjectRole.VIEWER);
            if (followUp) {
                return PageResponse.from(drafts.findVisibleByProjectDueForFollowUp(projectId, userId, page.pageable()),
                        ProjectOutreachResponse::from);
            }
            return PageResponse.from(status == null
                    ? drafts.findVisibleByProject(projectId, userId, page.pageable())
                    : drafts.findVisibleByProjectAndStatus(projectId, userId, status, page.pageable()),
                    ProjectOutreachResponse::from);
        });
    }

    /** Newest first. */
    public Mono<PageResponse<OutreachDraftResponse>> list(UUID userId, UUID locationId, PageQuery page) {
        return db.call(() -> {
            access.location(userId, locationId, ProjectRole.VIEWER);
            return PageResponse.from(drafts.findVisibleByLocation(locationId, userId, page.pageable()), OutreachDraftResponse::from);
        });
    }

    public Mono<OutreachDraftResponse> get(UUID userId, UUID draftId) {
        return db.call(() -> OutreachDraftResponse.from(access.draft(userId, draftId, ProjectRole.VIEWER)));
    }

    /**
     * Full replacement of the editable fields: a null recipient name or email clears it. Moving the draft
     * out of {@code DRAFT} stamps {@code sentAt}, moving it back clears it, see {@link OutreachDraft#changeStatus}.
     */
    public Mono<OutreachDraftResponse> update(UUID userId, UUID draftId, UpdateOutreachRequest request) {
        return db.call(() -> {
            OutreachDraft draft = access.draft(userId, draftId, ProjectRole.EDITOR);
            OutreachStatus before = draft.getStatus();
            if (before != request.status()) {
                Location venue = draft.getLocation();
                activity.record(venue.getScene().getProject(), userId, ActivityVerb.OUTREACH_STATUS_CHANGED, ActivityTarget.OUTREACH_DRAFT,
                        draft.getId(), ActivityLog.facts("venue", venue.getName(), "locationId", venue.getId().toString(),
                                "from", before.name(), "to", request.status().name()));
            }
            draft.setSubject(request.subject().strip());
            draft.setBody(request.body().strip());
            draft.setTone(request.tone());
            draft.setRecipientName(blankToNull(request.recipientName()));
            draft.setRecipientEmail(blankToNull(request.recipientEmail()));
            draft.changeStatus(request.status());
            return OutreachDraftResponse.from(drafts.saveAndFlush(draft));
        });
    }

    public Mono<Void> delete(UUID userId, UUID draftId) {
        return db.run(() -> drafts.delete(access.draft(userId, draftId, ProjectRole.EDITOR)));
    }


    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
