package com.cinescout.logistics;

import com.cinescout.logistics.LogisticsReport.SectionStatus;
import com.cinescout.logistics.LogisticsReport.UnitBase;
import com.cinescout.logistics.places.Place;
import com.cinescout.logistics.places.PlaceKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnitBaseFinderTest {

    private static Place base(String name, double distance, Integer capacity, Double area, String type) {
        return new Place(PlaceKind.UNIT_BASE, name, new GeoPoint(51.5, -0.1), distance, new Place.Size(capacity, area, type));
    }

    @Test
    void theBiggestComeFirstWhereTheMapSaysThenTheNearest() {
        UnitBase found = UnitBaseFinder.find(List.of(
                base("Unsized near", 50, null, null, null),
                base("Small lot", 300, 20, null, "surface"),        // 500 m²
                base("Big outline", 900, null, 4000.0, "surface"),  // 4,000 m²
                base("Big lot", 600, 200, null, "surface"),         // 5,000 m²
                base("Unsized far", 400, null, null, "street_side"),
                new Place(PlaceKind.PARKING, "Crew parking", new GeoPoint(51.5, -0.1), 10)), 1000);

        assertThat(found.status()).isEqualTo(SectionStatus.OK);
        assertThat(found.sites()).extracting(LogisticsReport.UnitBaseSite::name)
                .containsExactly("Big lot", "Big outline", "Small lot", "Unsized near", "Unsized far");
        assertThat(found.sites()).extracting(LogisticsReport.UnitBaseSite::kind)
                .containsExactly("Open car park", "Open car park", "Open car park", "Car park", "Roadside bays (lay-by)");
        assertThat(found.sites().get(1).areaSquareMeters()).isEqualTo(4000);
    }

    @Test
    void aShortListAndPlainWordsWhenThereIsNothing() {
        List<Place> many = java.util.stream.IntStream.range(0, 10).mapToObj(i -> base("Lot " + i, 100 + i, null, null, null)).toList();
        assertThat(UnitBaseFinder.find(many, 1000).sites()).hasSize(UnitBaseFinder.LIMIT);

        UnitBase none = UnitBaseFinder.find(List.of(), 1000);
        assertThat(none.sites()).isEmpty();
        assertThat(none.message()).isEqualTo("Nothing on the map within 1000 m: ask the location about parking");
    }
}
