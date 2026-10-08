package com.cinescout.logistics.solar;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ShotLightTest {

    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-06-21T12:00:00+01:00");

    @Test
    void theCameraFacingTheSunIsBacklitAwayFromItFrontLitAcrossItSideLit() {
        SunPosition south = SunPosition.of(AT, 180, 60);
        assertThat(ShotLight.of(south, 170).light()).isEqualTo(ShotLight.Light.BACKLIT);
        assertThat(ShotLight.of(south, 10).light()).isEqualTo(ShotLight.Light.FRONT_LIT);
        assertThat(ShotLight.of(south, 90).light()).isEqualTo(ShotLight.Light.SIDE_LIT);
        assertThat(ShotLight.of(SunPosition.of(AT, 350, 30), 20).light()).isEqualTo(ShotLight.Light.BACKLIT); // across north
        assertThat(ShotLight.of(south, 170).text()).contains("backlit", "Sun from S");
    }

    @Test
    void aLowSunIsGoldenABelowTheHorizonSunIsDownAndNoBearingStillSaysWhereTheSunIs() {
        ShotLight low = ShotLight.of(SunPosition.of(AT, 300, 3), 120);
        assertThat(low.golden()).isTrue();
        assertThat(low.light()).isEqualTo(ShotLight.Light.FRONT_LIT);
        assertThat(ShotLight.of(SunPosition.of(AT, 330, -5), 120).light()).isEqualTo(ShotLight.Light.SUN_DOWN);
        ShotLight unknown = ShotLight.of(SunPosition.of(AT, 180, 40), null);
        assertThat(unknown.light()).isNull();
        assertThat(unknown.text()).contains("Set which way the camera faces");
    }

    @Test
    void anglesWrapRoundNorth() {
        assertThat(ShotLight.angleBetween(10, 350)).isEqualTo(20);
        assertThat(ShotLight.angleBetween(180, 0)).isEqualTo(180);
        assertThat(ShotLight.angleBetween(90, 90)).isZero();
    }
}
