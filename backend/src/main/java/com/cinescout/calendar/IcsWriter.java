package com.cinescout.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Writes an iCalendar (RFC 5545) document: content lines end in CRLF, are folded so none is longer than 75 octets
 * (a fold never splits a UTF-8 character), and text values are escaped.
 */
final class IcsWriter {

    private static final int MAX_OCTETS = 75;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final StringBuilder out = new StringBuilder();

    /** A property whose value is written as given (dates, codes, URIs). */
    IcsWriter raw(String name, String value) {
        return line(name + ":" + value);
    }

    /** A TEXT property: backslash, semicolon, comma and line breaks are escaped. */
    IcsWriter text(String name, String value) {
        return line(name + ":" + escape(value));
    }

    /** An all-day DATE value. */
    IcsWriter date(String name, LocalDate date) {
        return line(name + ";VALUE=DATE:" + DATE.format(date));
    }

    /** A floating DATE-TIME: the same clock time wherever the calendar is, as a shoot's call time is the venue's own. */
    IcsWriter localTime(String name, LocalDateTime time) {
        return line(name + ":" + LOCAL_TIME.format(time));
    }

    IcsWriter utcTime(String name, Instant time) {
        return line(name + ":" + UTC_TIME.format(time));
    }

    @Override
    public String toString() {
        return out.toString();
    }

    static String escape(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    /** Folds the line at 75 octets: each continuation starts with a space, which counts toward its own 75. */
    private IcsWriter line(String content) {
        int octets = 0;
        int limit = MAX_OCTETS;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > limit) {
                out.append("\r\n ");
                octets = 0;
                limit = MAX_OCTETS - 1;
            }
            out.appendCodePoint(codePoint);
            octets += size;
            i += Character.charCount(codePoint);
        }
        out.append("\r\n");
        return this;
    }
}
