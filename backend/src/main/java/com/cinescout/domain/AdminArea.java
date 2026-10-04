package com.cinescout.domain;

import java.util.List;

/**
 * The local authority area a position falls in, as the geocoder names it, e.g. "London Borough of Camden" with the
 * codes ["GB-CMD", "GB-ENG"]. Kept on a venue with the position it was looked up for, so a moved venue is looked up
 * again.
 *
 * @param codes       ISO 3166-2 codes of the area and the areas around it, most local first
 * @param countryCode ISO 3166-1 alpha-2, lower case ("gb"); null when not known
 */
public record AdminArea(String name, List<String> codes, String countryCode, double latitude, double longitude) {

    public AdminArea {
        codes = codes == null ? List.of() : List.copyOf(codes);
    }

    /** Whether this was looked up for (about) this position: within a few metres. */
    public boolean isFor(double lat, double lng) {
        return Math.abs(latitude - lat) < 0.0001 && Math.abs(longitude - lng) < 0.0001;
    }
}
