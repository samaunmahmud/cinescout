package com.cinescout.logistics.solar;

import java.util.Locale;

/**
 * The natural light a scene is set in, as a band of sun elevations (degrees above the horizon) and, for
 * dawn and dusk, the half of the day it falls in. Derived from the free-text time of day the scene parser
 * extracts ("dusk", "night", "golden hour"...), so a scout sees when that light actually happens.
 */
public enum SceneLight {

    DAWN(-6, 6, Period.MORNING),
    DUSK(-6, 6, Period.EVENING),
    GOLDEN_HOUR(-4, 6, Period.ANY),
    BLUE_HOUR(-6, -4, Period.ANY),
    NIGHT(-90, -6, Period.ANY),
    DAY(6, 90, Period.ANY);

    enum Period { MORNING, EVENING, ANY }

    private final double minElevation;
    private final double maxElevation;
    private final Period period;

    SceneLight(double minElevation, double maxElevation, Period period) {
        this.minElevation = minElevation;
        this.maxElevation = maxElevation;
        this.period = period;
    }

    double minElevation() { return minElevation; }
    double maxElevation() { return maxElevation; }
    Period period() { return period; }

    /**
     * Reads the parser's time of day. The more specific phrases are checked first, so "daybreak" is dawn
     * rather than day and "golden hour in the evening" is dusk.
     *
     * @return null if the text names no natural light (e.g. "interior, any time") or is null
     */
    public static SceneLight fromTimeOfDay(String timeOfDay) {
        if (timeOfDay == null) {
            return null;
        }
        String text = timeOfDay.toLowerCase(Locale.ROOT);
        if (containsAny(text, "blue hour", "twilight")) {
            return BLUE_HOUR;
        }
        if (containsAny(text, "dawn", "sunrise", "first light", "daybreak")) {
            return DAWN;
        }
        if (containsAny(text, "dusk", "sunset", "last light", "sundown")) {
            return DUSK;
        }
        if (containsAny(text, "golden", "magic hour")) {
            if (text.contains("morning")) {
                return DAWN;
            }
            return containsAny(text, "evening", "afternoon") ? DUSK : GOLDEN_HOUR;
        }
        if (containsAny(text, "night", "midnight")) {
            return NIGHT;
        }
        if (containsAny(text, "day", "morning", "afternoon", "noon")) {
            return DAY;
        }
        return null;
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
