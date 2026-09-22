package com.cinescout.logistics.places.overpass;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.ProviderHttp;
import com.cinescout.logistics.places.PlaceKind;
import com.cinescout.logistics.places.overpass.OsmRules.Rule;

import java.util.List;
import java.util.Map;

/**
 * Builds the one Overpass QL query that fetches every {@link PlaceKind} around a point. Each tag value gets
 * its own exact-match clause: Overpass can look {@code key=value} up in its index, whereas a regular
 * expression over the values made the same query take twice as long (measured against the public server).
 */
final class OverpassQuery {

    private OverpassQuery() {
    }

    /**
     * Areas and points come back with their tags and a centre; lines with their tags and geometry, so
     * the distance to their nearest stretch can be measured. Tunnels are left out of the line query.
     *
     * @param serverTimeoutSeconds how long Overpass may spend before giving up (it answers with a remark)
     */
    static String around(GeoPoint point, int serverTimeoutSeconds) {
        String at = ProviderHttp.coordinate(point.latitude()) + "," + ProviderHttp.coordinate(point.longitude());
        StringBuilder query = new StringBuilder("[out:json][timeout:").append(serverTimeoutSeconds).append("];\n(\n");
        for (Map.Entry<PlaceKind, Rule> entry : OsmRules.RULES.entrySet()) {
            if (!entry.getValue().line()) {
                for (String filter : filters(entry.getValue())) {
                    query.append("  nwr").append(filter).append(around(entry.getKey(), at)).append(";\n");
                }
            }
        }
        query.append(");\nout tags center qt;\n(\n");
        for (Map.Entry<PlaceKind, Rule> entry : OsmRules.RULES.entrySet()) {
            if (entry.getValue().line()) {
                for (String filter : filters(entry.getValue())) {
                    query.append("  way").append(filter).append("[\"tunnel\"!=\"yes\"]").append(around(entry.getKey(), at)).append(";\n");
                }
            }
        }
        return query.append(");\nout tags geom qt;\n").toString();
    }

    /** One {@code ["key"="value"]} per value, sorted so the query is the same every time. */
    private static List<String> filters(Rule rule) {
        return rule.values().stream().sorted().map(value -> "[\"" + rule.key() + "\"=\"" + value + "\"]").toList();
    }

    private static String around(PlaceKind kind, String at) {
        return "(around:" + kind.radiusMeters() + "," + at + ")";
    }
}
