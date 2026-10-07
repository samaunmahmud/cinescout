package com.cinescout.logistics.places.overpass;

import com.cinescout.logistics.places.PlaceKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How each {@link PlaceKind} is tagged in OpenStreetMap. The same table builds the Overpass query and
 * classifies what comes back, so the two cannot drift apart.
 */
final class OsmRules {

    /**
     * @param key    the OSM tag key
     * @param values the tag values that mean this kind
     * @param line   a linear feature (a railway, a road): fetched with its geometry so the distance is
     *               to its nearest stretch rather than to its middle, which may be kilometres away
     */
    record Rule(String key, Set<String> values, boolean line, Map<String, Set<String>> also) {

        Rule(String key, Set<String> values, boolean line) {
            this(key, values, line, Map.of());
        }

        /** Every key with its values: the main one first, then the others in key order. */
        List<Map.Entry<String, Set<String>>> tags() {
            List<Map.Entry<String, Set<String>>> tags = new ArrayList<>();
            tags.add(Map.entry(key, values));
            also.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(tags::add);
            return tags;
        }

        boolean matches(Map<String, String> osm) {
            return tags().stream().anyMatch(tag -> osm.get(tag.getKey()) != null && tag.getValue().contains(osm.get(tag.getKey())));
        }
    }

    /** Car parks a truck cannot get into, or that are a lane of the road itself. */
    private static final Set<String> NOT_FOR_TRUCKS = Set.of("underground", "multi-storey", "rooftop", "garage_boxes", "carports", "lane");

    /** Values of {@code access} that mean the public cannot use the place (a private car park). */
    private static final Set<String> NO_PUBLIC_ACCESS = Set.of("private", "no", "customers", "permit");

    static final Map<PlaceKind, Rule> RULES = rules();

    private OsmRules() {
    }

    private static Map<PlaceKind, Rule> rules() {
        Map<PlaceKind, Rule> rules = new EnumMap<>(PlaceKind.class);
        rules.put(PlaceKind.HOSPITAL, area("amenity", "hospital"));
        rules.put(PlaceKind.PHARMACY, area("amenity", "pharmacy"));
        rules.put(PlaceKind.PARKING, area("amenity", "parking"));
        rules.put(PlaceKind.FOOD, area("amenity", "cafe", "restaurant", "fast_food"));
        rules.put(PlaceKind.TOILETS, area("amenity", "toilets"));
        rules.put(PlaceKind.FUEL, area("amenity", "fuel"));
        rules.put(PlaceKind.LODGING, area("tourism", "hotel", "motel", "guest_house", "hostel"));
        rules.put(PlaceKind.HARDWARE, area("shop", "hardware", "doityourself"));
        rules.put(PlaceKind.GROCERY, area("shop", "supermarket", "convenience"));
        rules.put(PlaceKind.AIRPORT, area("aeroway", "aerodrome"));
        rules.put(PlaceKind.HELIPORT, area("aeroway", "heliport"));
        rules.put(PlaceKind.STADIUM, area("leisure", "stadium"));
        rules.put(PlaceKind.RAILWAY, new Rule("railway", Set.of("rail", "light_rail", "subway", "tram"), true));
        rules.put(PlaceKind.EMERGENCY_STATION, area("amenity", "fire_station", "police"));
        rules.put(PlaceKind.CONSTRUCTION, area("landuse", "construction"));
        rules.put(PlaceKind.MAJOR_ROAD, new Rule("highway", Set.of("motorway", "trunk", "primary"), true));
        rules.put(PlaceKind.SCHOOL, area("amenity", "school", "kindergarten"));
        rules.put(PlaceKind.NIGHTLIFE, area("amenity", "bar", "pub", "nightclub"));
        rules.put(PlaceKind.PLACE_OF_WORSHIP, area("amenity", "place_of_worship"));
        rules.put(PlaceKind.UNIT_BASE, new Rule("amenity", Set.of("parking"), false, Map.of("highway", Set.of("rest_area"))));
        if (rules.size() != PlaceKind.values().length) {
            throw new IllegalStateException("Every PlaceKind needs an OpenStreetMap rule");
        }
        return Collections.unmodifiableMap(rules); // keeps enum order, so the query is deterministic
    }

    private static Rule area(String key, String... values) {
        return new Rule(key, Set.of(values), false);
    }

    /** The kinds an element with these tags counts as (usually one, none for most noise in the data). */
    static List<PlaceKind> classify(Map<String, String> tags) {
        List<PlaceKind> kinds = new ArrayList<>();
        if (tags == null) {
            return kinds;
        }
        for (PlaceKind kind : PlaceKind.values()) {
            Rule rule = RULES.get(kind);
            if (!rule.matches(tags)) {
                continue;
            }
            if (kind == PlaceKind.UNIT_BASE && tags.get("parking") != null && NOT_FOR_TRUCKS.contains(tags.get("parking"))) {
                continue;
            }
            if (rule.line() && "yes".equals(tags.get("tunnel"))) {
                continue; // a line in a tunnel is not heard on the street
            }
            String access = tags.get("access"); // Set.of(...).contains(null) would throw
            if (kind.group() != PlaceKind.Group.NOISE && access != null && NO_PUBLIC_ACCESS.contains(access)) {
                continue;
            }
            kinds.add(kind);
        }
        return kinds;
    }
}
