package com.cinescout.service;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.domain.OutreachDraft;
import com.cinescout.domain.OutreachReply;
import com.cinescout.domain.OutreachStatus;
import com.cinescout.domain.ProjectRole;
import com.cinescout.dto.ManualReplyRequest;
import com.cinescout.dto.OutreachReplyResponse;
import com.cinescout.dto.PageQuery;
import com.cinescout.dto.PageResponse;
import com.cinescout.mail.InboundMessage;
import com.cinescout.mail.ReplyAddresses;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.OutreachDraftRepository;
import com.cinescout.repository.OutreachReplyRepository;
import com.cinescout.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Replies to outreach email: matched by a draft's reply address when they come in by mail, or pasted in by a
 * member. Either way the draft moves to REPLIED. Reply text is kept for the crew to read and is never given to the
 * AI.
 */
@Service
public class ReplyService {

    private static final Logger log = LoggerFactory.getLogger(ReplyService.class);

    private final OutreachReplyRepository replies;
    private final OutreachDraftRepository drafts;
    private final UserRepository users;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public ReplyService(OutreachReplyRepository replies, OutreachDraftRepository drafts, UserRepository users, ProjectAccess access,
                        BlockingTransactions db) {
        this.replies = replies;
        this.drafts = drafts;
        this.users = users;
        this.access = access;
        this.db = db;
    }

    public Mono<PageResponse<OutreachReplyResponse>> list(UUID userId, UUID draftId, PageQuery page) {
        return db.call(() -> {
            access.draft(userId, draftId, ProjectRole.VIEWER);
            return PageResponse.from(replies.findByDraft(draftId, page.pageable()), OutreachReplyResponse::from);
        });
    }

    public Mono<OutreachReplyResponse> record(UUID userId, UUID draftId, ManualReplyRequest request) {
        return db.call(() -> {
            OutreachDraft draft = access.draft(userId, draftId, ProjectRole.EDITOR);
            OutreachReply reply = replies.saveAndFlush(new OutreachReply(draft, OutreachReply.Source.MANUAL, request.fromAddress(),
                    request.fromName(), request.subject(), request.text(),
                    request.receivedAt() == null ? DatabaseTime.now() : request.receivedAt(), users.getReferenceById(userId)));
            markReplied(draft);
            return OutreachReplyResponse.from(reply);
        });
    }

    /**
     * Files an email that came in, by the reply address among its recipients. One for no draft (mistyped, or the draft
     * deleted) is dropped quietly: the provider should not keep retrying it.
     *
     * @return whether it was filed against a draft
     */
    public Mono<Boolean> receive(InboundMessage message) {
        return db.call(() -> {
            Optional<OutreachDraft> match = message.recipients().stream()
                    .map(ReplyAddresses.current()::tokenOf)
                    .flatMap(Optional::stream)
                    .findFirst()
                    .flatMap(drafts::findByReplyToken);
            if (match.isEmpty()) {
                log.info("An inbound email matched no outreach draft; dropped");
                return false;
            }
            OutreachDraft draft = match.get();
            replies.saveAndFlush(new OutreachReply(draft, OutreachReply.Source.INBOUND, message.fromAddress(), message.fromName(),
                    message.subject(), message.text(), message.receivedAt(), null));
            markReplied(draft);
            return true;
        });
    }

    private void markReplied(OutreachDraft draft) {
        if (draft.getStatus() != OutreachStatus.REPLIED) {
            draft.changeStatus(OutreachStatus.REPLIED);
            drafts.saveAndFlush(draft);
        }
    }
}
