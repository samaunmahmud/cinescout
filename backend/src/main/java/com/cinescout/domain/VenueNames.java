package com.cinescout.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compares venue names the way a person would: "The Blue Note", "BLUE NOTE!" and "Blue Note" are the same name.
 * Case, accents, punctuation and a leading "the" do not count, and "&" reads as "and".
 */
public final class VenueNames {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private VenueNames() {
    }

    /** The name as lower-case words without accents or punctuation ("&" as "and"), and without a leading "the"; empty for none. */
    public static List<String> words(String name) {
        if (name == null) {
            return List.of();
        }
        String plain = MARKS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("").replace("&", " and ");
        List<String> words = new ArrayList<>(Arrays.stream(NON_ALPHANUMERIC.split(plain.toLowerCase(Locale.ROOT)))
                .filter(word -> !word.isEmpty()).toList());
        if (words.size() > 1 && words.getFirst().equals("the")) {
            words.removeFirst();
        }
        return words;
    }

    /** A key two names share exactly when they are the same name; null when there is no name to compare. */
    public static String key(String name) {
        List<String> words = words(name);
        return words.isEmpty() ? null : String.join(" ", words);
    }
}
