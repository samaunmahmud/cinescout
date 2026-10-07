package com.cinescout.calendar;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IcsWriterTest {

    @Test
    void textIsEscapedAndDatesAreWrittenAsRfc5545Wants() {
        String ics = new IcsWriter()
                .text("LOCATION", "Tom's Diner, 782 Washington Ave; Brooklyn\\NY\nback door")
                .date("DTSTART", LocalDate.of(2026, 10, 12))
                .localTime("DTSTART", LocalDateTime.of(2026, 10, 12, 7, 30))
                .toString();

        assertThat(ics).isEqualTo("LOCATION:Tom's Diner\\, 782 Washington Ave\\; Brooklyn\\\\NY\\nback door\r\n"
                + "DTSTART;VALUE=DATE:20261012\r\n"
                + "DTSTART:20261012T073000\r\n");
    }

    @Test
    void longLinesFoldAt75OctetsWithoutSplittingACharacter() {
        String value = "Café ".repeat(40) + "🎬 end";
        String ics = new IcsWriter().text("DESCRIPTION", value).toString();

        String[] lines = ics.split("\r\n");
        assertThat(lines.length).isGreaterThan(3);
        for (String line : lines) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        for (int i = 1; i < lines.length; i++) {
            assertThat(lines[i]).startsWith(" ");
        }
        // Unfolding (dropping each CRLF and the space after it) gives the line back.
        assertThat(ics.replace("\r\n ", "")).isEqualTo("DESCRIPTION:" + value + "\r\n");
    }
}
