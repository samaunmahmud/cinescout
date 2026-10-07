package com.cinescout.logistics.places.overpass;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.places.PlaceKind;
import com.cinescout.logistics.places.overpass.OsmRules.Rule;

import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the one Overpass QL query that fetches every {@link PlaceKind} around a point. Each tag value gets
 * its own exact-match clause: Overpass can look {@code key=value} up in its index, whereas a regular
 * expression over the values made the same query take twice as long (measured against the public server).
 * A clause two kinds share (a car park is parking for the crew's cars and a possible unit base) is asked for
 * once, at the wider of their radii.
 */
final class OverpassQuery {

    private OverpassQuery() {
    }

    static String around(GeoPoint point, int serverTimeoutSeconds) {
        return around(point, serverTimeoutSeconds, PlaceKind.UNIT_BASE.radiusMeters());
    }

    /**
     * Areas and points come back with their tags, a centre (points) and bounds (ways and relations, from which
     * a centre and a rough size are taken); lines with their tags and geometry, so the distance to their nearest
     * stretch can be measured. Tunnels are left out of the line query.
     *
     * @param serverTimeoutSeconds how long Overpass may spend before giving up (it answers with a remark)
     * @param unitBaseRadius       how far to look for somewhere to park the trucks
     */
    static String around(GeoPoint point, int serverTimeoutSeconds, int unitBaseRadius) {
        String at = ProviderHttp.coordinate(point.latitude()) + "," + ProviderHttp.coordinate(point.longitude());
        StringBuilder query = new StringBuilder("[out:json][timeout:").append(serverTimeoutSeconds).append("];\n(\n");
        clauses(false, unitBaseRadius).forEach((clause, radius) ->
                query.append("  nwr").append(clause).append("(around:").append(radius).append(",").append(at).append(");\n"));
        query.append(");\nout tags center bb qt;\n(\n");
        clauses(true, unitBaseRadius).forEach((clause, radius) ->
                query.append("  way").append(clause).append("[\"tunnel\"!=\"yes\"]").append("(around:").append(radius).append(",").append(at).append(");\n"));
        return query.append(");\nout tags geom qt;\n").toString();
    }

    /** Each {@code ["key"="value"]} once, with the widest radius of the kinds that use it, sorted so the query is the same every time. */
    private static Map<String, Integer> clauses(boolean lines, int unitBaseRadius) {
        Map<String, Integer> clauses = new TreeMap<>();
        for (Map.Entry<PlaceKind, Rule> entry : OsmRules.RULES.entrySet()) {
            if (entry.getValue().line() != lines) {
                continue;
            }
            int radius = entry.getKey() == PlaceKind.UNIT_BASE ? unitBaseRadius : entry.getKey().radiusMeters();
            for (Map.Entry<String, java.util.Set<String>> tag : entry.getValue().tags()) {
                for (String value : tag.getValue()) {
                    clauses.merge("[\"" + tag.getKey() + "\"=\"" + value + "\"]", radius, Math::max);
                }
            }
        }
        return clauses;
    }
}
