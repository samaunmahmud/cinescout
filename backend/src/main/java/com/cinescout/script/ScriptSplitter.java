package com.cinescout.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuts a screenplay into its scenes at the scene headings ("INT. DINER - NIGHT"), so a whole script can be
 * added to a project in one go. Plain text only: no AI, nothing leaves the server.
 *
 * <p>A heading is a line that starts with INT, EXT or a mix of the two (INT./EXT., I/E) or EST, optionally
 * with the scene number a shooting script prints on one or both sides. Such a line counts when it is written
 * in capitals or stands after a blank line, which keeps a sentence like "Int. shots are rare here" inside a
 * paragraph from starting a scene. Fountain's forced headings (".BINOCULARS POV") and scene numbers
 * ("#12#") are understood too. Whatever comes before the first heading (title page, FADE IN) is left out.
 */
public final class ScriptSplitter {

    private static final String NUMBER = "(\\d{1,4})([A-Za-z]{0,2})";

    private static final Pattern HEADING = Pattern.compile(
            "^(?:" + NUMBER + "[.)]?\\s+)?"
                    + "((?:INT\\.?\\s*/\\s*EXT|EXT\\.?\\s*/\\s*INT|I\\s*/\\s*E|INT|EXT|EST)(?:\\.[ \\t]*|[ \\t]+[-:]?[ \\t]*|[-:][ \\t]*)\\S.*)$",
            Pattern.CASE_INSENSITIVE);

    /** Fountain: a line starting with one full stop is a heading, whatever it says. */
    private static final Pattern FORCED_HEADING = Pattern.compile("^\\.([\\p{L}\\p{N}].*)$");

    /** Fountain's scene number, "#12#", at the end of a heading. */
    private static final Pattern FOUNTAIN_NUMBER = Pattern.compile("^(.*?)\\s*#\\s*([^#]*?)\\s*#$");

    private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*\\S)\\s+" + NUMBER + "\\.?$");

    private ScriptSplitter() {
    }

    /** The script's scenes, in order; empty when it has no scene headings. */
    public static List<ScriptScene> split(String script) {
        String[] lines = script.replace("\r\n", "\n").replace('\r', '\n').replace("\f", "").split("\n", -1);
        List<ScriptScene> scenes = new ArrayList<>();
        Heading current = null;
        StringBuilder text = new StringBuilder();
        boolean afterBlank = true;
        for (String line : lines) {
            String stripped = line.strip();
            Heading heading = heading(stripped, afterBlank);
            if (heading != null) {
                add(scenes, current, text);
                current = heading;
                text = new StringBuilder(stripped);
            } else if (current != null) {
                text.append('\n').append(line.stripTrailing());
            }
            afterBlank = stripped.isEmpty();
        }
        add(scenes, current, text);
        return scenes;
    }

    private static void add(List<ScriptScene> scenes, Heading heading, StringBuilder text) {
        if (heading != null) {
            scenes.add(new ScriptScene(heading.number(), heading.title(), text.toString().stripTrailing()));
        }
    }

    private record Heading(Integer number, String title) {
    }

    private static Heading heading(String line, boolean afterBlank) {
        if (line.isEmpty()) {
            return null;
        }
        String rest = line;
        String fountainNumber = null;
        Matcher numbered = FOUNTAIN_NUMBER.matcher(rest);
        if (numbered.matches()) {
            rest = numbered.group(1);
            fountainNumber = numbered.group(2);
        }
        Matcher forced = FORCED_HEADING.matcher(rest);
        if (forced.matches()) {
            return new Heading(wholeNumber(fountainNumber), tidy(forced.group(1)));
        }
        Matcher match = HEADING.matcher(rest);
        if (!match.matches() || !(afterBlank || rest.equals(rest.toUpperCase(Locale.ROOT)))) {
            return null;
        }
        String leading = match.group(1) == null ? null : match.group(1) + match.group(2);
        String title = match.group(3);
        // "12 INT. DINER - NIGHT 12": the number repeated on the right is not part of the heading. A number
        // that stands there alone is kept, as it is more often a place ("EXT. ROUTE 66") than a scene number.
        Matcher trailing = TRAILING_NUMBER.matcher(title);
        if (leading != null && trailing.matches() && leading.equalsIgnoreCase(trailing.group(2) + trailing.group(3))) {
            title = trailing.group(1);
        }
        return new Heading(wholeNumber(fountainNumber != null ? fountainNumber : leading), tidy(title));
    }

    private static Integer wholeNumber(String number) {
        if (number == null || !number.matches("\\d{1,4}")) {
            return null;
        }
        int value = Integer.parseInt(number);
        return value > 0 ? value : null;
    }

    private static String tidy(String title) {
        return title.strip().replaceAll("\\s+", " ");
    }
}
