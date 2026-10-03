package com.cinescout.jobs;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.OutreachDraftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Flags the outreach emails that have waited longer than their project's follow-up days without a reply, so the crew
 * sees a "Follow up" badge. One update statement; an email already flagged, answered or followed up is left alone, so
 * the job can run as often as it is called.
 */
@Component
class FollowUpJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(FollowUpJob.class);

    private final OutreachDraftRepository drafts;
    private final BlockingTransactions db;

    FollowUpJob(OutreachDraftRepository drafts, BlockingTransactions db) {
        this.drafts = drafts;
        this.db = db;
    }

    @Override
    public String name() {
        return "follow-ups";
    }

    @Override
    public Mono<JobRun> run() {
        return db.call(drafts::flagOverdue).map(flagged -> {
            if (flagged > 0) {
                log.info("Flagged {} outreach email(s) for a follow-up", flagged);
            }
            return new JobRun(name(), flagged, DatabaseTime.now());
        });
    }
}
