package com.cinescout.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * What a reference photo shows, as the vision model reads it: the kind of place, its look, the features that make it,
 * and a phrase to search for places like it. Doubles as the model's JSON output schema.
 *
 * @param settingType  the kind of place, searchable ("1950s American diner")
 * @param visualMood   its look and atmosphere ("pastel, chrome, nostalgic")
 * @param features     what gives it that look (pink vinyl booths, a jukebox); the first 8 are kept, each cut to 120
 *                     characters (models do not always keep to a schema's array and length limits, and a long list is
 *                     not worth failing the run for)
 * @param searchPhrase what to search for to find real places like it
 */
public record PhotoLook(
        @NotBlank @Size(max = 200) String settingType,
        @Size(max = 300) String visualMood,
        List<String> features,
        @NotBlank @Size(max = 200) String searchPhrase
) {

    static final int MAX_FEATURES = 8;
    static final int MAX_FEATURE_LENGTH = 120;

    public PhotoLook {
        features = features == null ? List.of() : features.stream()
                .filter(feature -> feature != null && !feature.isBlank())
                .map(String::strip)
                .map(feature -> feature.length() > MAX_FEATURE_LENGTH ? feature.substring(0, MAX_FEATURE_LENGTH).strip() : feature)
                .limit(MAX_FEATURES)
                .toList();
    }
}
