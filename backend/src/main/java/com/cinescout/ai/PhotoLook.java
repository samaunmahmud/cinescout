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
 * @param features     what gives it that look (pink vinyl booths, a jukebox), at most 8
 * @param searchPhrase what to search for to find real places like it
 */
public record PhotoLook(
        @NotBlank @Size(max = 200) String settingType,
        @Size(max = 300) String visualMood,
        @Size(max = 8) List<@NotBlank @Size(max = 120) String> features,
        @NotBlank @Size(max = 200) String searchPhrase
) {

    public PhotoLook {
        features = features == null ? List.of() : List.copyOf(features);
    }
}
