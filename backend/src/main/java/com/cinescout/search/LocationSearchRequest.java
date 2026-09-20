package com.cinescout.search;

import com.cinescout.domain.SceneRequirements;

/**
 * What to search for and where.
 *
 * @param requirements what the scene needs; the setting type is mandatory, since a search
 *                     without one cannot find anything meaningful (the LLM may have returned null)
 * @param area         free-text place to search in, e.g. {@code "Brooklyn, New York"}
 * @param maxResults   upper bound on hits returned, 1 to {@value #MAX_RESULTS}
 */
public record LocationSearchRequest(SceneRequirements requirements, String area, int maxResults) {

    public static final int DEFAULT_MAX_RESULTS = 10;
    public static final int MAX_RESULTS = 20;

    public LocationSearchRequest {
        if (requirements == null) {
            throw new IllegalArgumentException("requirements must not be null");
        }
        if (requirements.settingType() == null || requirements.settingType().isBlank()) {
            throw new IllegalArgumentException("requirements must include a setting type to search for");
        }
        if (area == null || area.isBlank()) {
            throw new IllegalArgumentException("area must not be blank");
        }
        area = area.strip();
        if (maxResults < 1 || maxResults > MAX_RESULTS) {
            throw new IllegalArgumentException("maxResults must be between 1 and " + MAX_RESULTS);
        }
    }

    public static LocationSearchRequest of(SceneRequirements requirements, String area) {
        return new LocationSearchRequest(requirements, area, DEFAULT_MAX_RESULTS);
    }
}
