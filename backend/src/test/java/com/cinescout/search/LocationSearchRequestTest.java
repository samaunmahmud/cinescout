package com.cinescout.search;

import com.cinescout.domain.AcousticSensitivity;
import com.cinescout.domain.SceneRequirements;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationSearchRequestTest {

    private static final SceneRequirements ROOFTOP =
            new SceneRequirements("rooftop bar", "neon noir", null, "night", AcousticSensitivity.HIGH, 12);

    @Test
    void ofUsesTheDefaultResultCountAndStripsTheArea() {
        LocationSearchRequest request = LocationSearchRequest.of(ROOFTOP, "  Brooklyn, NY ");

        assertThat(request.area()).isEqualTo("Brooklyn, NY");
        assertThat(request.maxResults()).isEqualTo(LocationSearchRequest.DEFAULT_MAX_RESULTS);
    }

    @Test
    void aSettingTypeIsRequiredBecauseTheLlmMayHaveReturnedNone() {
        SceneRequirements noSetting = new SceneRequirements(null, "moody", null, null, null, null);
        SceneRequirements blankSetting = new SceneRequirements("  ", "moody", null, null, null, null);

        assertThatThrownBy(() -> LocationSearchRequest.of(noSetting, "Brooklyn")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LocationSearchRequest.of(blankSetting, "Brooklyn")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingRequirementsAndBlankAreas() {
        assertThatThrownBy(() -> LocationSearchRequest.of(null, "Brooklyn")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LocationSearchRequest.of(ROOFTOP, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LocationSearchRequest.of(ROOFTOP, "   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultCountMustBeWithinTheSupportedRange() {
        assertThat(new LocationSearchRequest(ROOFTOP, "Brooklyn", 1).maxResults()).isEqualTo(1);
        assertThat(new LocationSearchRequest(ROOFTOP, "Brooklyn", LocationSearchRequest.MAX_RESULTS).maxResults())
                .isEqualTo(LocationSearchRequest.MAX_RESULTS);
        assertThatThrownBy(() -> new LocationSearchRequest(ROOFTOP, "Brooklyn", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocationSearchRequest(ROOFTOP, "Brooklyn", LocationSearchRequest.MAX_RESULTS + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
