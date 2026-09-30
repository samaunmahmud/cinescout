package com.cinescout.resilience;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Starts calls to a rate-limited service at an even pace: never two less than {@code interval} apart, however
 * they arrive. That is stricter than "so many per clock second", which lets a burst that straddles the turn of
 * a second through all at once, and a service that counts over a sliding second then refuses part of it.
 *
 * <p>A call waits its turn without blocking a thread. One that would wait longer than {@code maxWait} is not
 * made. Turns are handed out in arrival order and are not given back: a caller that cancels while waiting
 * leaves a gap, which only errs on the slow side.
 */
public final class Pacer {

    private final long intervalNanos;
    private final long maxWaitNanos;
    /** When the next call may start, on the {@link System#nanoTime()} clock. */
    private final AtomicLong nextStart = new AtomicLong(System.nanoTime());

    public Pacer(Duration interval, Duration maxWait) {
        this.intervalNanos = interval.toNanos();
        this.maxWaitNanos = maxWait.toNanos();
    }

    /**
     * Makes the call when its turn comes. The supplier is invoked then, not before.
     *
     * @param tooBusy the error to emit, without making the call, when the wait would exceed {@code maxWait}
     */
    public <T> Mono<T> pace(Supplier<Mono<T>> call, Supplier<? extends Throwable> tooBusy) {
        return Mono.defer(() -> {
            long wait = reserve();
            if (wait < 0) {
                return Mono.error(tooBusy.get());
            }
            return wait == 0 ? Mono.defer(call) : Mono.delay(Duration.ofNanos(wait)).then(Mono.defer(call));
        });
    }

    /** Takes the next turn and returns how long to wait for it, in nanoseconds; -1 if that is too long. */
    private long reserve() {
        while (true) {
            long now = System.nanoTime();
            long next = nextStart.get();
            long start = next - now > 0 ? next : now;
            if (start - now > maxWaitNanos) {
                return -1;
            }
            if (nextStart.compareAndSet(next, start + intervalNanos)) {
                return start - now;
            }
        }
    }
}
