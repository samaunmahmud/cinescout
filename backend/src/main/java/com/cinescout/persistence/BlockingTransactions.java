package com.cinescout.persistence;

import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Supplier;

/**
 * The bridge between the reactive web layer and blocking JPA: each unit of work runs in its own
 * short transaction on {@code boundedElastic}, never on an event-loop thread. Keep the work to
 * database access only; never call an LLM or another network service inside it.
 */
public class BlockingTransactions {

    private final TransactionTemplate tx;

    public BlockingTransactions(TransactionTemplate tx) {
        this.tx = tx;
    }

    /** Runs {@code work} in a transaction and emits its (non-null) result. */
    public <T> Mono<T> call(Supplier<T> work) {
        return Mono.fromCallable(() -> tx.execute(status -> work.get())).subscribeOn(Schedulers.boundedElastic());
    }

    /** Runs {@code work} in a transaction and completes when it has committed. */
    public Mono<Void> run(Runnable work) {
        return call(() -> {
            work.run();
            return Boolean.TRUE;
        }).then();
    }
}
