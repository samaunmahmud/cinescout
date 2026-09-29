package com.cinescout.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    private static final int MIN_SINGLE_WORD_LETTERS = 5;

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

    /**
     * Whether two names are very likely the same venue: the same words; the same letters once spaces are gone
     * ("Bond Street Studio" and "bondstreet.studio"); one name of at least two words found, in order, in the other
     * ("Meili Rooftop" in "MEILI Rooftop Restaurant"); or a one-word name of at least
     * {@value #MIN_SINGLE_WORD_LETTERS} letters that starts the other ("Meili" and "MEILI Rooftop Restaurant").
     * Meant for results of one search in one area, where two different venues rarely share that much of a name.
     * False when either has no name.
     */
    public static boolean sameVenue(String a, String b) {
        List<String> first = words(a);
        List<String> second = words(b);
        if (first.isEmpty() || second.isEmpty()) {
            return false;
        }
        if (String.join("", first).equals(String.join("", second))) {
            return true;
        }
        List<String> shorter = first.size() <= second.size() ? first : second;
        List<String> longer = shorter == first ? second : first;
        if (shorter.size() == 1) {
            return shorter.getFirst().length() >= MIN_SINGLE_WORD_LETTERS && longer.getFirst().equals(shorter.getFirst());
        }
        return Collections.indexOfSubList(longer, shorter) >= 0;
    }

    /**
     * Whether two addresses give the same building: the same street number and street, i.e. the same words before
     * the first comma, starting with a number ("829 Broadway, Brooklyn, NY 11206" and "829 Broadway, Brooklyn").
     * Differently written streets ("N 12th St" and "North 12th Street") do not match. False when either is missing.
     */
    public static boolean sameStreetAddress(String a, String b) {
        List<String> first = words(streetPart(a));
        List<String> second = words(streetPart(b));
        return first.size() >= 2 && first.getFirst().chars().anyMatch(Character::isDigit) && first.equals(second);
    }

    private static String streetPart(String address) {
        if (address == null) {
            return null;
        }
        int comma = address.indexOf(',');
        return comma < 0 ? address : address.substring(0, comma);
    }
}
