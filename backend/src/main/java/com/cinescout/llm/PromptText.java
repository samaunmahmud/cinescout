package com.cinescout.llm;

import java.util.regex.Pattern;

/**
 * Helpers for putting untrusted text (a script, a scraped web page, a user's free-text note) into a
 * prompt. They are the first line of defence against prompt injection; the schema-validated output
 * is the real guard.
 */
public final class PromptText {

    private static final int MAX_FIELD_CHARS = 200;

    private PromptText() {
    }

    /** Wraps untrusted text in a tag it cannot close early, whatever it contains. */
    public static String fence(String tag, String text) {
        String safe = Pattern.compile("<\\s*/\\s*" + Pattern.quote(tag) + "\\s*>", Pattern.CASE_INSENSITIVE)
                .matcher(text).replaceAll("[/" + tag + "]");
        return "<" + tag + ">\n" + safe.strip() + "\n</" + tag + ">\n";
    }

    /** Model- or web-supplied short text: one line, bounded. */
    public static String oneLine(String value) {
        String collapsed = value.strip().replaceAll("\\s+", " ");
        return collapsed.length() > MAX_FIELD_CHARS ? collapsed.substring(0, MAX_FIELD_CHARS) + "..." : collapsed;
    }
}
