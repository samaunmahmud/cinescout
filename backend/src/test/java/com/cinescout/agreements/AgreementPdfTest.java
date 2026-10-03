package com.cinescout.agreements;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class AgreementPdfTest {

    private static AgreementFacts facts(String address) {
        return new AgreementFacts("The Night Ferry", "Harbour Films Ltd", "Ada Lovell", "Starlite Diner", address, "Rita Moss",
                "rita@starlite.example", "+1 718 555 0100", "£1,200 a day", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3),
                LocalTime.of(17, 0), LocalTime.of(2, 0), 25, 2, LocalDate.of(2026, 10, 4));
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static int pages(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    @Test
    void theReleaseIsFilledInFromTheFacts() throws IOException {
        String text = text(AgreementPdf.write(facts("12 Example Ave, Brooklyn")));

        assertThat(text).contains("LOCATION RELEASE", "TEMPLATE", "version 2", "Production: The Night Ferry",
                "Production company: Harbour Films Ltd", "Venue: Starlite Diner", "Address: 12 Example Ave, Brooklyn",
                "Owner or manager: Rita Moss", "rita@starlite.example", "+1 718 555 0100", "Location fee: £1,200 a day",
                "Monday 2 November 2026 to Tuesday 3 November 2026", "from 17:00 to 02:00 (the next morning)", "About 25 (an estimate)",
                "For the production", "For the owner");
    }

    @Test
    void whatIsNotKnownIsLeftBlankToFillIn() throws IOException {
        AgreementFacts bare = new AgreementFacts("The Night Ferry", null, null, "Starlite Diner", null, null, null, null, null, null, null,
                null, null, null, 1, LocalDate.of(2026, 10, 4));

        String text = text(AgreementPdf.write(bare));

        assertThat(text).contains("Address: " + AgreementPdf.BLANK, "Location fee: " + AgreementPdf.BLANK, "Shoot dates: " + AgreementPdf.BLANK,
                "Cast and crew: " + AgreementPdf.BLANK);
    }

    @Test
    void everyPageSaysItIsATemplateAndNotLegalAdvice() throws IOException {
        byte[] pdf = AgreementPdf.write(facts("A very long address ".repeat(200)));

        assertThat(pages(pdf)).isGreaterThan(1);
        String text = text(pdf);
        int footers = text.split("Template only — not legal advice\\. Have it reviewed before signing\\.", -1).length - 1;
        assertThat(footers).isEqualTo(pages(pdf));
        assertThat(text).contains("page 1 of " + pages(pdf));
    }

    @Test
    void charactersTheFontCannotShowDoNotBreakIt() throws IOException {
        String text = text(AgreementPdf.write(facts("東京 1-2-3, Café Straße\tline")));

        assertThat(text).contains("?? 1-2-3, Café Straße line");
    }

    @Test
    void theFileIsNamedAfterTheVenueAndVersion() {
        assertThat(AgreementService.filename("The Starlite Diner!", 3)).isEqualTo("location-release-the-starlite-diner-v3.pdf");
        assertThat(AgreementService.filename("東京", 1)).isEqualTo("location-release-venue-v1.pdf");
    }
}
