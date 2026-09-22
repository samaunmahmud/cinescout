package com.cinescout.logistics.places.overpass;

import com.cinescout.logistics.GeoPoint;
import com.cinescout.logistics.places.PlaceKind;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class OverpassQueryTest {

    private static final String QUERY = OverpassQuery.around(new GeoPoint(40.7128, -74.006), 25);

    @Test
    void asksForJsonWithTheServerTimeout() {
        assertThat(QUERY).startsWith("[out:json][timeout:25];");
    }

    @Test
    void areasComeWithCentresAndLinesWithGeometryAndNoTunnels() {
        assertThat(QUERY)
                .contains("nwr[\"amenity\"=\"cafe\"](around:600,40.712800,-74.006000);")
                .contains("nwr[\"amenity\"=\"restaurant\"](around:600,40.712800,-74.006000);")
                .contains("nwr[\"aeroway\"=\"aerodrome\"](around:5000,40.712800,-74.006000);")
                .contains("way[\"railway\"=\"subway\"][\"tunnel\"!=\"yes\"](around:500,40.712800,-74.006000);")
                .contains("way[\"highway\"=\"trunk\"][\"tunnel\"!=\"yes\"](around:300,40.712800,-74.006000);");
        assertThat(QUERY.indexOf("out tags center qt;")).isLessThan(QUERY.indexOf("way[\"railway\""));
        assertThat(QUERY).endsWith("out tags geom qt;\n");
    }

    @Test
    void tagValuesAreMatchedExactlyNeverByRegularExpression() {
        assertThat(QUERY).doesNotContain("~");
    }

    @Test
    void everyTagValueOfEveryKindIsQueriedExactlyOnce() {
        for (Map.Entry<PlaceKind, OsmRules.Rule> entry : OsmRules.RULES.entrySet()) {
            for (String value : entry.getValue().values()) {
                String clause = (entry.getValue().line() ? "way" : "nwr")
                        + "[\"" + entry.getValue().key() + "\"=\"" + value + "\"]";
                assertThat(QUERY.split(Pattern.quote(clause), -1)).as(entry.getKey() + " " + value).hasSize(2);
            }
        }
    }

    @Test
    void theQueryIsTheSameEveryTime() {
        assertThat(OverpassQuery.around(new GeoPoint(40.7128, -74.006), 25)).isEqualTo(QUERY);
    }
}
