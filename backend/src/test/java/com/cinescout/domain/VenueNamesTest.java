package com.cinescout.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VenueNamesTest {

    @Test
    void wordsIgnoreCaseAccentsPunctuationAndALeadingThe() {
        assertThat(VenueNames.words("  Crème—Brûlée, the Bar ")).containsExactly("creme", "brulee", "the", "bar");
        assertThat(VenueNames.words("The Blue Note")).containsExactly("blue", "note");
        assertThat(VenueNames.words("The")).containsExactly("the"); // a name that is only "the" keeps it
        assertThat(VenueNames.words(null)).isEmpty();
    }

    @Test
    void theSameNameWrittenDifferentlyHasTheSameKey() {
        assertThat(VenueNames.key("Golden Blue Bar & Restaurant")).isEqualTo(VenueNames.key("golden blue bar and restaurant"));
        assertThat(VenueNames.key("Bar&Grill")).isEqualTo("bar and grill");
        assertThat(VenueNames.key("The Wythe Hotel")).isEqualTo(VenueNames.key("WYTHE HOTEL!"));
        assertThat(VenueNames.key("Wythe Hotel")).isNotEqualTo(VenueNames.key("Wythe Hotel Rooftop"));
        assertThat(VenueNames.key(" ,. ")).isNull();
        assertThat(VenueNames.key(null)).isNull();
    }
}
