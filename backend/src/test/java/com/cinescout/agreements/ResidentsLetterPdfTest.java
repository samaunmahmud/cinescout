package com.cinescout.agreements;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ResidentsLetterPdfTest {

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    @Test
    void theLetterSaysWhoWhenWhatAndWhoToCall() throws IOException {
        ResidentsLetterFacts facts = new ResidentsLetterFacts("The Night Ferry", "Harbour Films Ltd", "Starlite Diner", "12 Example Ave, Brooklyn",
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), LocalTime.of(17, 0), LocalTime.of(2, 0), 25, true, "Ada Lovell",
                "+44 7700 900123", "ada@harbour.example", "Two trucks on Dock Street, suspended bays 4-9.", "A rain machine runs for an hour.",
                LocalDate.of(2026, 10, 14));

        String text = text(ResidentsLetterPdf.write(facts));

        assertThat(text).contains("FILMING NEAR YOU", "The Night Ferry", "Dear Neighbour",
                "Harbour Films Ltd will be filming scenes for The Night Ferry at Starlite Diner, 12 Example Ave, Brooklyn",
                "Monday 2 November 2026 to Tuesday 3 November 2026", "From 5:00 PM to 2:00 AM the next morning",
                "About 25 cast and crew", "Two trucks on Dock Street, suspended bays 4-9.", "Part of the filming happens at night",
                "A rain machine runs for an hour.", "Ada Lovell", "Phone: +44 7700 900123", "Email: ada@harbour.example");
        assertThat(text).doesNotContain(ResidentsLetterPdf.DEFAULT_PARKING);
    }

    @Test
    void whatIsNotKnownIsWordedRoundNotLeftBlank() throws IOException {
        ResidentsLetterFacts bare = new ResidentsLetterFacts("The Night Ferry", null, "Starlite Diner", null, null, null, null, null, null,
                false, null, null, null, null, null, LocalDate.of(2026, 10, 14));

        String text = text(ResidentsLetterPdf.write(bare));

        assertThat(text).contains("Our crew will be filming scenes for The Night Ferry at Starlite Diner.",
                "The dates are still being settled", "A film crew coming and going", ResidentsLetterPdf.DEFAULT_PARKING,
                "Ask any member of the crew on the day", "The location team");
        assertThat(text).doesNotContain("at night", "Phone:", "null");
    }

    @Test
    void hoursAreSaidPlainly() {
        assertThat(ResidentsLetterPdf.hours(LocalTime.of(7, 0), LocalTime.of(19, 30)))
                .isEqualTo("From 7:00 AM to 7:30 PM, including setting up and packing away");
        assertThat(ResidentsLetterPdf.hours(LocalTime.of(7, 0), null)).isEqualTo("From about 7:00 AM each day");
        assertThat(ResidentsLetterPdf.hours(null, null)).isNull();
    }

    @Test
    void nightWorkIsANightSceneOrALateOrOvernightWrap() {
        assertThat(ResidentsLetterService.night("NIGHT", null, null)).isTrue();
        assertThat(ResidentsLetterService.night("Late night", LocalTime.of(9, 0), LocalTime.of(18, 0))).isTrue();
        assertThat(ResidentsLetterService.night("DAY", LocalTime.of(9, 0), LocalTime.of(22, 30))).isTrue();
        assertThat(ResidentsLetterService.night("DAY", LocalTime.of(17, 0), LocalTime.of(2, 0))).isTrue();
        assertThat(ResidentsLetterService.night("DAY", LocalTime.of(7, 0), LocalTime.of(19, 0))).isFalse();
        assertThat(ResidentsLetterService.night(null, null, null)).isFalse();
    }

    @Test
    void theFileIsNamedAfterTheVenue() {
        assertThat(ResidentsLetterService.filename("The Starlite Diner!")).isEqualTo("letter-to-neighbours-the-starlite-diner.pdf");
        assertThat(ResidentsLetterService.filename("東京")).isEqualTo("letter-to-neighbours-venue.pdf");
    }
}
