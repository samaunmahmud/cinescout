package com.cinescout.script;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The speaking characters of a scene, read from its screenplay format: a character's cue is a line in capitals,
 * on its own, with their dialogue (or a parenthetical) on the next line. Extensions such as (V.O.), (O.S.) and
 * (CONT'D) are dropped, so a voice-over is the same person. No AI: the format says it.
 */
public final class ScriptCharacters {

    private static final int MAX_NAME_LENGTH = 30;
    /** Capitals, digits and the punctuation names carry ("DR. KAPLAN", "O'NEILL", "MAN #2", "JEAN-LUC"). */
    private static final Pattern CUE = Pattern.compile("[\\p{Lu}0-9][\\p{Lu}0-9 .'#&-]*(\\s*\\([^)]*\\))*\\s*(\\^)?");
    private static final Pattern EXTENSION = Pattern.compile("\\s*\\([^)]*\\)");
    private static final Set<String> NOT_NAMES = Set.of("FADE IN", "FADE OUT", "FADE TO BLACK", "CUT TO", "THE END", "CONTINUED",
            "MORE", "BACK TO", "LATER", "SUPER", "TITLE", "INTERCUT", "END OF FLASHBACK", "FLASHBACK", "MONTAGE", "END MONTAGE",
            "SERIES OF SHOTS", "CLOSE ON", "ANGLE ON", "BLACK", "OMITTED");

    private ScriptCharacters() {
    }

    /** The characters who speak, in the order they first do. */
    public static List<String> in(String sceneText) {
        String[] lines = sceneText.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; i + 1 < lines.length; i++) {
            String line = lines[i].strip();
            String next = lines[i + 1].strip();
            if (next.isEmpty() || !CUE.matcher(line).matches() || !hasLowerCase(next) && !next.startsWith("(")) {
                continue;
            }
            String name = EXTENSION.matcher(line).replaceAll("").replace("^", "").strip().replaceAll("\\s+", " ");
            if (isName(name)) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    private static boolean isName(String name) {
        if (name.length() < 2 || name.length() > MAX_NAME_LENGTH || !name.chars().anyMatch(Character::isLetter)) {
            return false;
        }
        String bare = name.replaceAll("[.:]+$", "");
        String upper = bare.toUpperCase(Locale.ROOT);
        return !NOT_NAMES.contains(upper) && !upper.endsWith(" TO") && !upper.startsWith("INT") && !upper.startsWith("EXT")
                && !upper.startsWith("I/E") && !upper.startsWith("EST");
    }

    private static boolean hasLowerCase(String text) {
        return text.chars().anyMatch(Character::isLowerCase);
    }
}
