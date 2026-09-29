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
    void theSameVenueWrittenDifferentlyIsRecognised() {
        assertThat(VenueNames.sameVenue("Golden Blue Bar & Restaurant", "golden blue bar and restaurant")).isTrue();
        assertThat(VenueNames.sameVenue("The Wythe Hotel", "WYTHE HOTEL!")).isTrue();
        assertThat(VenueNames.sameVenue("Bond Street Studio", "bondstreet.studio")).isTrue();
        assertThat(VenueNames.sameVenue("Meili Rooftop", "MEILI Rooftop Restaurant")).isTrue();
        assertThat(VenueNames.sameVenue("Bar&Grill", "bar and grill")).isTrue();
        assertThat(VenueNames.sameVenue("MEILI", "Meili Rooftop Restaurant")).as("a distinctive first word").isTrue();
    }

    @Test
    void theSameStreetNumberAndStreetIsTheSameBuilding() {
        assertThat(VenueNames.sameStreetAddress("829 Broadway, Brooklyn, NY 11206", "829 broadway, Brooklyn")).isTrue();
        assertThat(VenueNames.sameStreetAddress("829 Broadway", "831 Broadway")).isFalse();
        assertThat(VenueNames.sameStreetAddress("160 N 12th St", "160 North 12th Street")).as("written differently").isFalse();
        assertThat(VenueNames.sameStreetAddress("Broadway, Brooklyn", "Broadway, Brooklyn")).as("no street number").isFalse();
        assertThat(VenueNames.sameStreetAddress(null, null)).isFalse();
    }

    @Test
    void differentVenuesAreNotConfused() {
        assertThat(VenueNames.sameVenue("Bar", "Bar Blondeau")).as("one short word is too little to go on").isFalse();
        assertThat(VenueNames.sameVenue("Rooftop", "Golden Rooftop")).as("a single word only matches the start").isFalse();
        assertThat(VenueNames.sameVenue("Rooftop Bar", "Bar Rooftop")).as("words out of order").isFalse();
        assertThat(VenueNames.sameVenue("Golden Blue", "Golden Child")).isFalse();
        assertThat(VenueNames.sameVenue(" ,. ", " ,. ")).as("no name at all").isFalse();
        assertThat(VenueNames.sameVenue(null, "Anything")).isFalse();
    }
}
