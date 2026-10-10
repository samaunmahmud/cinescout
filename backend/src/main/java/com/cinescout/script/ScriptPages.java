package com.cinescout.script;

import java.util.regex.Pattern;

/**
 * How long a scene runs on the page, in eighths of a page, as a stripboard counts it. The text is laid out the way a
 * standard screenplay page sets it (Courier 12, about 54 lines a page): action wraps at 61 characters, dialogue at 35,
 * and a run of blank lines counts as one. A scene is never shorter than an eighth. No AI: the layout says it.
 */
public final class ScriptPages {

    static final int LINES_PER_PAGE = 54;
    private static final int ACTION_WIDTH = 61;
    private static final int DIALOGUE_WIDTH = 35;
    /** A character cue: capitals on their own line ("MARA (CONT'D)"); same rule as {@link ScriptCharacters}. */
    private static final Pattern CUE = Pattern.compile("[\\p{Lu}0-9][\\p{Lu}0-9 .'#&-]*(\\s*\\([^)]*\\))*\\s*(\\^)?");

    private ScriptPages() {
    }

    /** The scene's length in eighths of a page; at least 1. */
    public static int eighths(String sceneText) {
        String[] lines = sceneText.replace("\r\n", "\n").replace('\r', '\n').strip().split("\n", -1);
        int rows = 0;
        boolean dialogue = false;
        boolean lastBlank = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                if (!lastBlank) {
                    rows++;
                }
                lastBlank = true;
                dialogue = false;
                continue;
            }
            lastBlank = false;
            String next = i + 1 < lines.length ? lines[i + 1].strip() : "";
            if (!dialogue && isCue(line, next)) {
                rows++;
                dialogue = true;
                continue;
            }
            rows += wrapped(line, dialogue ? DIALOGUE_WIDTH : ACTION_WIDTH);
        }
        return Math.max(1, Math.round(rows * 8f / LINES_PER_PAGE));
    }

    private static boolean isCue(String line, String next) {
        return CUE.matcher(line).matches() && !next.isEmpty() && (next.chars().anyMatch(Character::isLowerCase) || next.startsWith("("));
    }

    /** How many printed lines a line of text takes at the given width, breaking between words. */
    private static int wrapped(String line, int width) {
        int rows = 1;
        int used = 0;
        for (String word : line.split("\\s+")) {
            int length = word.length();
            if (used == 0) {
                rows += (Math.max(length, 1) - 1) / width;
                used = (length - 1) % width + 1;
            } else if (used + 1 + length <= width) {
                used += 1 + length;
            } else {
                rows += 1 + (length - 1) / width;
                used = (length - 1) % width + 1;
            }
        }
        return rows;
    }
}
