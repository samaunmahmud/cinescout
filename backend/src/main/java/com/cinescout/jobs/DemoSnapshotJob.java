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
 * Saves each shared demo account's productions and library as they are now, as what {@link DemoResetJob} puts back.
 * Run by hand (it is on no timer) after setting the demo up the way it should stay.
 */
@Component
class DemoSnapshotJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DemoSnapshotJob.class);

    private final DemoSnapshots snapshots;
    private final SecurityProperties security;
    private final BlockingTransactions db;

    DemoSnapshotJob(DemoSnapshots snapshots, SecurityProperties security, BlockingTransactions db) {
        this.snapshots = snapshots;
        this.security = security;
        this.db = db;
    }

    @Override
    public String name() {
        return "demo-snapshot";
    }

    @Override
    public Mono<JobRun> run() {
        return db.call(() -> {
            int saved = 0;
            for (String email : security.lockedAccounts()) {
                UUID account = snapshots.account(email).orElse(null);
                if (account != null) {
                    int rows = snapshots.take(account);
                    log.info("Demo account {} snapshot taken: {} row(s)", account, rows);
                    saved += rows;
                }
            }
            return new JobRun(name(), saved, DatabaseTime.now());
        });
    }
}
