package com.cinescout.agreements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What a screenplay scene heading ("12 EXT. FERRY DECK - NIGHT") says about where and when. */
final class SceneHeading {

    /** Any heading: INT., EXT. or I/E, after an optional scene number. */
    private static final Pattern ANY = Pattern.compile("^\\s*(\\d+[A-Z]?\\.?\\s+)?(INT|EXT|I\\s*/\\s*E)\\b", Pattern.CASE_INSENSITIVE);
    /** EXT., INT./EXT., EXT./INT. or I/E. */
    private static final Pattern EXTERIOR = Pattern.compile("^\\s*(\\d+[A-Z]?\\.?\\s+)?(EXT|INT\\.?\\s*/\\s*EXT|EXT\\.?\\s*/\\s*INT|I\\s*/\\s*E)\\b",
            Pattern.CASE_INSENSITIVE);
    /** The time of day after the last dash: "- NIGHT", "— DAWN". */
    private static final Pattern TIME = Pattern.compile("\\s[-–—]\\s*([^-–—]+)$");

    private SceneHeading() {
    }

    /** The scene's title when it reads as a heading, else the first line of its script when that does, else the title. */
    static String of(String title, String sourceText) {
        if (title != null && ANY.matcher(title).find()) {
            return title.strip();
        }
        if (sourceText != null) {
            String first = sourceText.strip().lines().findFirst().orElse("").strip();
            if (ANY.matcher(first).find()) {
                return first;
            }
        }
        return title == null ? null : title.strip();
    }

    static boolean exterior(String heading) {
        return heading != null && EXTERIOR.matcher(heading).find();
    }

    /** "NIGHT" from "EXT. DECK - NIGHT"; null when the heading names no time. */
    static String timeOfDay(String heading) {
        if (heading == null || !ANY.matcher(heading).find()) {
            return null;
        }
        Matcher time = TIME.matcher(heading.strip());
        return time.find() ? time.group(1).strip() : null;
    }
}
