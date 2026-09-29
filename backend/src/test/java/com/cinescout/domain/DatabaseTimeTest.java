package com.cinescout.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Meaningful where the JVM's clock reads nanoseconds (Linux, so CI); on macOS it reads microseconds anyway. */
class DatabaseTimeTest {

    @Test
    void instantsHaveNoDigitsPostgresWouldRoundAway() {
        for (int i = 0; i < 100; i++) {
            Instant now = DatabaseTime.now();
            Instant ticked = DatabaseTime.clock().instant();

            assertThat(now.truncatedTo(ChronoUnit.MICROS)).isEqualTo(now);
            assertThat(ticked.truncatedTo(ChronoUnit.MICROS)).isEqualTo(ticked);
        }
    }
}
