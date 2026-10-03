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
 */
public record SearchHints(String near, Double radiusKm, List<String> excludedTypes, boolean privateAllowed) {

    public static final SearchHints NONE = new SearchHints(null, null, List.of(), true);

    public SearchHints {
        excludedTypes = excludedTypes == null ? List.of() : List.copyOf(excludedTypes);
    }
}
