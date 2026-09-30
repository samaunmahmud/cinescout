package com.cinescout.resilience;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PacerTest {

    private final List<Long> startedAt = new CopyOnWriteArrayList<>();

    private Mono<String> call(String answer) {
        startedAt.add(System.nanoTime());
        return Mono.just(answer);
    }

    private static Throwable tooBusy() {
        return new IllegalStateException("too busy");
    }

    @Test
    void callsThatArriveTogetherAreStartedAnIntervalApart() {
        Pacer pacer = new Pacer(Duration.ofMillis(150), Duration.ofSeconds(5));

        List<String> answers = Flux.range(0, 4).flatMap(i -> pacer.pace(() -> call("a" + i), PacerTest::tooBusy)).collectList().block();

        assertThat(answers).containsExactlyInAnyOrder("a0", "a1", "a2", "a3");
        List<Long> sorted = startedAt.stream().sorted().toList();
        for (int i = 1; i < sorted.size(); i++) {
            // A little slack for the timer's own precision.
            assertThat(Duration.ofNanos(sorted.get(i) - sorted.get(i - 1))).as("gap before call " + i).isGreaterThanOrEqualTo(Duration.ofMillis(140));
        }
    }

    @Test
    void theFirstCallAndOneAfterAQuietSpellStartAtOnce() throws InterruptedException {
        Pacer pacer = new Pacer(Duration.ofMillis(100), Duration.ofSeconds(5));
        long begin = System.nanoTime();
        pacer.pace(() -> call("first"), PacerTest::tooBusy).block();
        Thread.sleep(250);
        long later = System.nanoTime();
        pacer.pace(() -> call("second"), PacerTest::tooBusy).block();
        // Quiet time is not saved up: the call right after still waits its interval.
        pacer.pace(() -> call("third"), PacerTest::tooBusy).block();

        assertThat(Duration.ofNanos(startedAt.get(0) - begin)).isLessThan(Duration.ofMillis(50));
        assertThat(Duration.ofNanos(startedAt.get(1) - later)).isLessThan(Duration.ofMillis(50));
        assertThat(Duration.ofNanos(startedAt.get(2) - startedAt.get(1))).isGreaterThanOrEqualTo(Duration.ofMillis(90));
    }

    @Test
    void aCallThatWouldWaitTooLongIsNotMadeAndDoesNotTakeATurn() throws InterruptedException {
        Pacer pacer = new Pacer(Duration.ofMillis(300), Duration.ofMillis(50));
        pacer.pace(() -> call("first"), PacerTest::tooBusy).block();

        assertThatThrownBy(() -> pacer.pace(() -> call("second"), PacerTest::tooBusy).block())
                .isInstanceOf(IllegalStateException.class).hasMessage("too busy");
        assertThat(startedAt).hasSize(1);

        // The refused call took no turn: the next is due 300 ms after the first, which is within reach by now.
        Thread.sleep(260);
        pacer.pace(() -> call("third"), PacerTest::tooBusy).block();
        assertThat(startedAt).hasSize(2);
        assertThat(Duration.ofNanos(startedAt.get(1) - startedAt.get(0))).isBetween(Duration.ofMillis(290), Duration.ofMillis(500));
    }

    @Test
    void nothingIsCalledUntilSubscribedTo() {
        Pacer pacer = new Pacer(Duration.ofMillis(10), Duration.ofSeconds(1));

        pacer.pace(() -> call("never"), PacerTest::tooBusy);

        assertThat(startedAt).isEmpty();
    }
}
