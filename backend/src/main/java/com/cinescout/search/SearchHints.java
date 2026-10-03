package com.cinescout.search;

import java.util.List;

/**
 * What else a search should keep to, beyond the scene and the area: hints for the search provider, not hard
 * filters (scouting checks the results itself afterwards).
 *
 * @param near           where venues should be close to (an address, or "51.5072, -0.1276"); null for anywhere in
 *                       the area
 * @param radiusKm       how close, with {@code near}
 * @param excludedTypes  kinds of place not wanted; never null
 * @param privateAllowed false when private homes and privately owned places are not wanted
 * @param knownVenues    venues already found for the scene, so a new search looks past them; never null
 * @param avoid          why the crew passed on earlier venues, latest first, to steer away from the same problems;
 *                       never null
 */
public record SearchHints(String near, Double radiusKm, List<String> excludedTypes, boolean privateAllowed,
                          List<String> knownVenues, List<String> avoid) {

    public static final SearchHints NONE = new SearchHints(null, null, List.of(), true);

    public SearchHints {
        excludedTypes = excludedTypes == null ? List.of() : List.copyOf(excludedTypes);
        knownVenues = knownVenues == null ? List.of() : List.copyOf(knownVenues);
        avoid = avoid == null ? List.of() : List.copyOf(avoid);
    }

    public SearchHints(String near, Double radiusKm, List<String> excludedTypes, boolean privateAllowed) {
        this(near, radiusKm, excludedTypes, privateAllowed, List.of(), List.of());
    }
}
