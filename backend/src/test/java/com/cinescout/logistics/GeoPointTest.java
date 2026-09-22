package com.cinescout.logistics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GeoPointTest {

    private static final GeoPoint NEW_YORK = new GeoPoint(40.7128, -74.0060);
    private static final GeoPoint LONDON = new GeoPoint(51.5074, -0.1278);

    @Test
    void greatCircleDistanceBetweenCities() {
        assertThat(NEW_YORK.distanceTo(LONDON)).isCloseTo(5_570_000, within(10_000.0));
        assertThat(NEW_YORK.distanceTo(NEW_YORK)).isZero();
    }

    @Test
    void oneThousandthOfADegreeOfLatitudeIsAboutOneHundredAndElevenMetres() {
        assertThat(NEW_YORK.distanceTo(new GeoPoint(40.7138, -74.0060))).isCloseTo(111.2, within(0.5));
    }

    @Test
    void distanceToASegmentIsToItsNearestPointNotItsEnds() {
        GeoPoint west = new GeoPoint(40.7138, -74.0160);
        GeoPoint east = new GeoPoint(40.7138, -73.9960);
        // The segment runs east-west 0.001 degrees north of the point; its ends are about 840 m away.
        assertThat(NEW_YORK.distanceToSegment(west, east)).isCloseTo(111.2, within(1.0));
        assertThat(NEW_YORK.distanceTo(west)).isGreaterThan(800);
    }

    @Test
    void distanceToASegmentBeyondItsEndIsToThatEnd() {
        GeoPoint a = new GeoPoint(40.7138, -74.0060);
        GeoPoint b = new GeoPoint(40.7238, -74.0060);
        assertThat(NEW_YORK.distanceToSegment(a, b)).isCloseTo(NEW_YORK.distanceTo(a), within(1.0));
        assertThat(NEW_YORK.distanceToSegment(a, a)).isCloseTo(NEW_YORK.distanceTo(a), within(1.0));
    }

    @Test
    void rejectsCoordinatesOutOfRange() {
        assertThatThrownBy(() -> new GeoPoint(90.1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(0, -180.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
