package com.cinescout.jobs;

import com.cinescout.domain.DatabaseTime;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.security.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Puts each shared demo account ({@code cinescout.security.locked-accounts}) back as its snapshot left it, undoing
 * whatever visitors changed, added or deleted. An account without a snapshot yet gets one taken instead, so the first
 * run keeps things as they are. Only locked accounts are touched: a real account is never reset.
 */
@Component
class DemoResetJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DemoResetJob.class);

    private final DemoSnapshots snapshots;
    private final SecurityProperties security;
    private final BlockingTransactions db;

    DemoResetJob(DemoSnapshots snapshots, SecurityProperties security, BlockingTransactions db) {
        this.snapshots = snapshots;
        this.security = security;
        this.db = db;
    }

    @Override
    public String name() {
        return "demo-reset";
    }

    @Override
    public Mono<JobRun> run() {
        return db.call(() -> {
            int restored = 0;
            for (String email : security.lockedAccounts()) {
                UUID account = snapshots.account(email).orElse(null);
                if (account == null) {
                    continue;
                }
                int rows = snapshots.restore(account);
                if (rows < 0) {
                    log.info("Demo account {} had no snapshot: took one of {} row(s)", account, snapshots.take(account));
                } else {
                    log.info("Demo account {} reset: {} row(s) restored", account, rows);
                    restored += rows;
                }
            }
            return new JobRun(name(), restored, DatabaseTime.now());
        });
    }
}
