package com.cinescout.logistics.solar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SceneLightTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "night, NIGHT",
            "Late Night, NIGHT",
            "midnight, NIGHT",
            "dusk, DUSK",
            "sunset, DUSK",
            "evening golden hour, DUSK",
            "golden hour in the afternoon, DUSK",
            "dawn, DAWN",
            "daybreak, DAWN",
            "early morning golden hour, DAWN",
            "sunrise, DAWN",
            "golden hour, GOLDEN_HOUR",
            "magic hour, GOLDEN_HOUR",
            "blue hour, BLUE_HOUR",
            "evening twilight, BLUE_HOUR",
            "day, DAY",
            "midday, DAY",
            "late afternoon, DAY",
            "morning, DAY",
    })
    void readsTheParsersTimeOfDay(String timeOfDay, SceneLight expected) {
        assertThat(SceneLight.fromTimeOfDay(timeOfDay)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "interior, any time", "unspecified"})
    void textThatNamesNoNaturalLightIsUnknown(String timeOfDay) {
        assertThat(SceneLight.fromTimeOfDay(timeOfDay)).isNull();
    }
}
