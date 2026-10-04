package com.cinescout.logistics.solar;

import com.cinescout.logistics.GeoPoint;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Reference times are the published NOAA / timeanddate.com values; the calculator must be within two minutes. */
class SolarCalculatorTest {

    private static final GeoPoint NEW_YORK = new GeoPoint(40.7128, -74.0060);
    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    private static final GeoPoint LONDON = new GeoPoint(51.5074, -0.1278);
    private static final GeoPoint TROMSO = new GeoPoint(69.6496, 18.9560);
    private static final ZoneId TROMSO_ZONE = ZoneId.of("Europe/Oslo");

    private static void assertNear(OffsetDateTime actual, LocalTime expected) {
        assertThat(actual).isNotNull();
        long minutesApart = Math.abs(Duration.between(actual.toLocalTime(), expected).toMinutes());
        assertThat(minutesApart).as("%s vs expected %s", actual.toLocalTime(), expected).isLessThanOrEqualTo(2);
    }

    @Test
    void sunriseSunsetAndNoonInNewYorkAtTheSummerSolstice() {
        SolarDay day = SolarCalculator.day(LocalDate.of(2026, 6, 21), NEW_YORK, NEW_YORK_ZONE, null);

        assertNear(day.sunrise(), LocalTime.of(5, 25));
        assertNear(day.sunset(), LocalTime.of(20, 31));
        assertNear(day.solarNoon(), LocalTime.of(12, 58));
        assertThat(day.daylightMinutes()).isCloseTo(15 * 60 + 6, within(3));
        assertThat(day.sunrise().getOffset()).isEqualTo(ZoneOffset.ofHours(-4)); // daylight saving time
    }

    @Test
    void sunriseAndSunsetInLondonAtTheWinterSolstice() {
        SolarDay day = SolarCalculator.day(LocalDate.of(2026, 12, 21), LONDON, ZoneId.of("Europe/London"), null);

        assertNear(day.sunrise(), LocalTime.of(8, 4));
        assertNear(day.sunset(), LocalTime.of(15, 54));
        assertThat(day.sunrise().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void sunriseAndSunsetInNewYorkAtTheSeptemberEquinox() {
        SolarDay day = SolarCalculator.day(LocalDate.of(2026, 9, 22), NEW_YORK, NEW_YORK_ZONE, null);

        assertNear(day.sunrise(), LocalTime.of(6, 44));
        assertNear(day.sunset(), LocalTime.of(18, 53));
    }

    @Test
    void goldenHourStraddlesSunsetAndIsFollowedDirectlyByBlueHour() {
        SolarDay day = SolarCalculator.day(LocalDate.of(2026, 6, 21), NEW_YORK, NEW_YORK_ZONE, null);

        assertThat(day.goldenHours()).hasSize(2);
        assertThat(day.blueHours()).hasSize(2);
        TimeWindow morningGolden = day.goldenHours().getFirst();
        TimeWindow eveningGolden = day.goldenHours().getLast();
        TimeWindow morningBlue = day.blueHours().getFirst();
        TimeWindow eveningBlue = day.blueHours().getLast();

        assertThat(morningBlue.end()).isEqualTo(morningGolden.start());
        assertThat(morningGolden.start()).isBefore(day.sunrise());
        assertThat(morningGolden.end()).isAfter(day.sunrise());
        assertThat(eveningGolden.start()).isBefore(day.sunset());
        assertThat(eveningGolden.end()).isAfter(day.sunset());
        assertThat(eveningGolden.end()).isEqualTo(eveningBlue.start());
        // At 40 N in June the sun takes a little over an hour to fall from 6 to -4 degrees.
        assertThat(Duration.between(eveningGolden.start(), eveningGolden.end()).toMinutes()).isBetween(60L, 90L);
        assertThat(Duration.between(eveningBlue.start(), eveningBlue.end()).toMinutes()).isBetween(10L, 25L);
    }

    @Test
    void theMidnightSunNeverSetsAndHasNoBlueHour() {
        SolarDay day = SolarCalculator.day(LocalDate.of(2026, 6, 21), TROMSO, TROMSO_ZONE, null);

        assertThat(day.sunrise()).isNull();
        assertThat(day.sunset()).isNull();
        assertThat(day.daylightMinutes()).isEqualTo(24 * 60);
        assertThat(day.blueHours()).isEmpty();
    }

    @Test
    void thePolarNightNeverRisesButStillHasTwilight() {
        SolarDay day = SolarCalculator.day(LocalDate.of(2026, 12, 21), TROMSO, TROMSO_ZONE, null);

        assertThat(day.sunrise()).isNull();
        assertThat(day.sunset()).isNull();
        assertThat(day.daylightMinutes()).isZero();
        // Around noon the sun climbs to about 3 degrees below the horizon: golden-hour light, no sun.
        assertThat(day.goldenHours()).hasSize(1);
        assertThat(day.goldenHours().getFirst().start()).isBefore(day.solarNoon());
        assertThat(day.goldenHours().getFirst().end()).isAfter(day.solarNoon());
    }

    @Test
    void theShortDayWhenTheClocksGoForwardIsHandled() {
        SolarDay day = SolarCalculator.day(LocalDate.of(2026, 3, 8), NEW_YORK, NEW_YORK_ZONE, null);

        // Solar noon is 13:07 EDT that day and the sun is up for about 11 h 36 min.
        assertNear(day.sunrise(), LocalTime.of(7, 19));
        assertNear(day.sunset(), LocalTime.of(18, 55));
        assertThat(day.sunrise().getOffset()).isEqualTo(ZoneOffset.ofHours(-4));
    }

    @Test
    void duskWindowsAreTheEveningTwilightOnly() {
        SolarDay day = SolarCalculator.day(LocalDate.of(2026, 6, 21), NEW_YORK, NEW_YORK_ZONE, SceneLight.DUSK);

        assertThat(day.sceneWindows()).hasSize(1);
        TimeWindow dusk = day.sceneWindows().getFirst();
        assertThat(dusk.start()).isEqualTo(day.goldenHours().getLast().start());
        assertThat(dusk.end()).isEqualTo(day.blueHours().getLast().end());
    }

    @Test
    void dawnWindowsAreTheMorningTwilightOnly() {
        SolarDay day = SolarCalculator.day(LocalDate.of(2026, 6, 21), NEW_YORK, NEW_YORK_ZONE, SceneLight.DAWN);

        assertThat(day.sceneWindows()).hasSize(1);
        assertThat(day.sceneWindows().getFirst().start()).isEqualTo(day.blueHours().getFirst().start());
        assertThat(day.sceneWindows().getFirst().end()).isEqualTo(day.goldenHours().getFirst().end());
    }

    @Test
    void nightIsFullDarknessEitherSideOfMidnight() {
        SolarDay day = SolarCalculator.day(LocalDate.of(2026, 6, 21), NEW_YORK, NEW_YORK_ZONE, SceneLight.NIGHT);

        assertThat(day.sceneWindows()).hasSize(2);
        assertThat(day.sceneWindows().getFirst().start().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(day.sceneWindows().getFirst().end()).isEqualTo(day.blueHours().getFirst().start());
        assertThat(day.sceneWindows().getLast().start()).isEqualTo(day.blueHours().getLast().end());
        assertThat(day.sceneWindows().getLast().end().toLocalDate()).isEqualTo(LocalDate.of(2026, 6, 22));
    }

    @Test
    void dayIsTheSunAboveGoldenHour() {
        SolarDay day = SolarCalculator.day(LocalDate.of(2026, 6, 21), NEW_YORK, NEW_YORK_ZONE, SceneLight.DAY);

        assertThat(day.sceneWindows()).containsExactly(
                new TimeWindow(day.goldenHours().getFirst().end(), day.goldenHours().getLast().start()));
    }

    @Test
    void noSceneWindowsWhenTheLightIsUnknownOrNeverHappens() {
        assertThat(SolarCalculator.day(LocalDate.of(2026, 6, 21), NEW_YORK, NEW_YORK_ZONE, null).sceneWindows()).isEmpty();
        // No real darkness under the midnight sun.
        assertThat(SolarCalculator.day(LocalDate.of(2026, 6, 21), TROMSO, TROMSO_ZONE, SceneLight.NIGHT).sceneWindows()).isEmpty();
    }

    // --- where the sun is ------------------------------------------------------------------------------

    private static final GeoPoint SYDNEY = new GeoPoint(-33.8688, 151.2093);

    /**
     * Published values (timeanddate.com, NOAA): London at midsummer rises at about 49° (NE), sets at about 311° (NW) and
     * stands about 62° high at noon; New York rises at about 58°; Sydney's midwinter noon sun is due north, about 32.7°
     * high; at an equinox the sun rises due east.
     */
    @Test
    void azimuthAndElevationMatchPublishedReferenceValues() {
        ZoneId london = ZoneId.of("Europe/London");
        SolarDay midsummer = SolarCalculator.day(LocalDate.of(2026, 6, 21), LONDON, london, null);

        SunPosition rise = SolarCalculator.position(midsummer.sunrise(), LONDON);
        SunPosition noon = SolarCalculator.position(midsummer.solarNoon(), LONDON);
        SunPosition set = SolarCalculator.position(midsummer.sunset(), LONDON);
        assertThat(rise.azimuth()).isCloseTo(49.5, within(1.0));
        assertThat(rise.elevation()).isCloseTo(-0.8, within(0.5));
        assertThat(noon.azimuth()).isCloseTo(180, within(0.5));
        assertThat(noon.elevation()).isCloseTo(62.0, within(0.3));
        assertThat(set.azimuth()).isCloseTo(310.5, within(1.0));

        SolarDay newYork = SolarCalculator.day(LocalDate.of(2026, 6, 21), NEW_YORK, NEW_YORK_ZONE, null);
        assertThat(SolarCalculator.position(newYork.sunrise(), NEW_YORK).azimuth()).isCloseTo(57.9, within(1.0));
        assertThat(SolarCalculator.position(newYork.solarNoon(), NEW_YORK).elevation()).isCloseTo(72.7, within(0.3));

        SolarDay sydney = SolarCalculator.day(LocalDate.of(2026, 6, 21), SYDNEY, ZoneId.of("Australia/Sydney"), null);
        SunPosition sydneyNoon = SolarCalculator.position(sydney.solarNoon(), SYDNEY);
        assertThat(Math.min(sydneyNoon.azimuth(), 360 - sydneyNoon.azimuth())).isLessThan(0.5);
        assertThat(sydneyNoon.elevation()).isCloseTo(32.7, within(0.3));

        SolarDay equinox = SolarCalculator.day(LocalDate.of(2026, 3, 20), LONDON, london, null);
        assertThat(SolarCalculator.position(equinox.sunrise(), LONDON).azimuth()).isCloseTo(89.5, within(1.5));
        assertThat(SolarCalculator.position(equinox.solarNoon(), LONDON).elevation()).isCloseTo(38.5, within(0.5));
    }

    @Test
    void theAfternoonSunIsInTheWestAndTheMorningSunInTheEast() {
        ZoneId london = ZoneId.of("Europe/London");
        SunPosition afternoon = SolarCalculator.position(LocalDate.of(2026, 10, 12).atTime(16, 0).atZone(london).toOffsetDateTime(), LONDON);
        SunPosition morning = SolarCalculator.position(LocalDate.of(2026, 10, 12).atTime(9, 0).atZone(london).toOffsetDateTime(), LONDON);

        assertThat(afternoon.azimuth()).isBetween(200.0, 260.0);
        assertThat(afternoon.compass()).isIn("SW", "W");
        assertThat(morning.azimuth()).isBetween(110.0, 160.0);
    }

    @Test
    void positionsAreSaidInPlainWords() {
        OffsetDateTime at = OffsetDateTime.of(2026, 10, 12, 16, 0, 0, 0, ZoneOffset.ofHours(1));

        assertThat(SunPosition.of(at, 225.2, 18.4).text()).isEqualTo("Sun from SW (225°), 18° high at 16:00");
        assertThat(SunPosition.of(at, 271, -5.4).text()).isEqualTo("Sun 5° below the horizon (W, 271°) at 16:00");
        assertThat(SunPosition.compass(0)).isEqualTo("N");
        assertThat(SunPosition.compass(359.9)).isEqualTo("N");
        assertThat(SunPosition.compass(22.6)).isEqualTo("NE");
        assertThat(SunPosition.compass(180)).isEqualTo("S");
        assertThat(SunPosition.compass(337.4)).isEqualTo("NW");
    }

    @Test
    void theSunPathFollowsTheScenesCallAndWrapTimesAcrossMidnight() {
        ZoneId london = ZoneId.of("Europe/London");
        SolarDay night = SolarCalculator.day(LocalDate.of(2026, 10, 12), LONDON, london, null, LocalTime.of(17, 0), LocalTime.of(2, 0));

        assertThat(night.sunPath()).extracting(position -> position.at().toLocalDateTime().toString())
                .containsExactly("2026-10-12T17:00", "2026-10-12T21:30", "2026-10-13T02:00");
        assertThat(night.sunPath().getLast().elevation()).isNegative();

        assertThat(SolarCalculator.day(LocalDate.of(2026, 10, 12), LONDON, london, null, LocalTime.of(7, 30), null).sunPath()).hasSize(1);
        assertThat(SolarCalculator.day(LocalDate.of(2026, 10, 12), LONDON, london, null).sunPath()).isEmpty();
        SolarDay dusk = SolarCalculator.day(LocalDate.of(2026, 10, 12), LONDON, london, SceneLight.fromTimeOfDay("dusk"));
        assertThat(dusk.sunPath()).hasSize(3);
        assertThat(dusk.sunPath().getFirst().at()).isEqualTo(dusk.sceneWindows().getFirst().start());
    }
}
