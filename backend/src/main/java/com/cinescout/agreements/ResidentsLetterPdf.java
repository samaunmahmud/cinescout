package com.cinescout.agreements;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes the letter a production drops through the doors round a venue before a shoot, as an A4 PDF: who is filming,
 * when, what neighbours will notice, and who to call. Plain words, one page for a typical shoot.
 */
public final class ResidentsLetterPdf {

    /** Said when the user gives no parking plan. */
    static final String DEFAULT_PARKING = "Our vehicles will park legally, and we will keep pavements, driveways and entrances clear.";
    static final String NIGHT = "Part of the filming happens at night. We will keep noise to a minimum, turn work lights away from "
            + "windows, and ask the crew to keep their voices down outside.";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final float MARGIN = 64;
    private static final Color INK = new Color(17, 24, 39);
    private static final Color MUTED = new Color(90, 98, 112);
    private static final Color BRAND = new Color(209, 58, 33);

    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDDocument document = new PDDocument();
    private PDPageContentStream page;
    private float y;

    private ResidentsLetterPdf() {
    }

    /** The letter as PDF bytes. */
    public static byte[] write(ResidentsLetterFacts facts) {
        ResidentsLetterPdf pdf = new ResidentsLetterPdf();
        try (PDDocument document = pdf.document) {
            pdf.body(facts);
            pdf.page.close();
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle("Filming near you: " + PdfText.safe(facts.venueName(), pdf.regular));
            info.setCreator("CineScout");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the letter", e);
        }
    }

    private void body(ResidentsLetterFacts f) throws IOException {
        newPage();
        page.setNonStrokingColor(BRAND);
        page.addRect(MARGIN, y - 4, 36, 4);
        page.fill();
        gap(16);
        text("FILMING NEAR YOU", bold, 22, INK);
        text(f.production(), bold, 13, BRAND);
        text(DAY.format(f.preparedOn()), regular, 9, MUTED);
        gap(14);

        paragraph("Dear Neighbour,");
        String who = present(f.productionCompany()) ? f.productionCompany().strip() : "Our crew";
        String where = present(f.venueAddress()) ? f.venueName() + ", " + f.venueAddress().strip() : f.venueName();
        paragraph(who + " will be filming scenes for " + f.production() + " at " + where
                + ". We are writing to let you know before we arrive, and to say thank you in advance for your patience.");

        heading("When");
        String days = AgreementPdf.dates(f.shootStart(), f.shootEnd());
        line(days == null ? "The dates are still being settled; we will write again once they are fixed." : days);
        String hours = hours(f.callTime(), f.wrapTime());
        if (hours != null) {
            line(hours);
        }

        heading("What you may notice");
        List<String> notices = new ArrayList<>();
        notices.add(f.crewSize() == null || f.crewSize() <= 0
                ? "A film crew coming and going, with cameras, lights and equipment."
                : "About " + f.crewSize() + " cast and crew coming and going, with cameras, lights and equipment.");
        notices.add(present(f.parking()) ? f.parking().strip() : DEFAULT_PARKING);
        if (f.night()) {
            notices.add(NIGHT);
        }
        if (present(f.note())) {
            notices.add(f.note().strip());
        }
        for (String notice : notices) {
            bullet(notice);
        }
        gap(6);
        paragraph("We will do all we can to keep disruption to a minimum, and we will leave the area as we found it.");

        heading("Questions or concerns");
        paragraph("If anything worries you, before or during the shoot, please get in touch. We would much rather hear from you "
                + "early and put it right.");
        boolean anyContact = present(f.contactName()) || present(f.contactPhone()) || present(f.contactEmail());
        if (present(f.contactName())) {
            line(f.contactName().strip(), bold);
        }
        if (present(f.contactPhone())) {
            line("Phone: " + f.contactPhone().strip());
        }
        if (present(f.contactEmail())) {
            line("Email: " + f.contactEmail().strip());
        }
        if (!anyContact) {
            line("Ask any member of the crew on the day for the location manager.");
        }

        gap(14);
        paragraph("Thank you,");
        line(present(f.contactName()) ? f.contactName().strip() : "The location team", bold);
        line("on behalf of " + f.production() + (present(f.productionCompany()) ? ", " + f.productionCompany().strip() : ""), regular);
    }

    /** "From 7:00 AM to 7:00 PM each day", with the next morning said when the wrap is past midnight; null with neither. */
    static String hours(LocalTime call, LocalTime wrap) {
        if (call == null && wrap == null) {
            return null;
        }
        if (wrap == null) {
            return "From about " + TIME.format(call) + " each day";
        }
        if (call == null) {
            return "Until about " + TIME.format(wrap) + " each day";
        }
        boolean overnight = !wrap.isAfter(call);
        return "From " + TIME.format(call) + " to " + TIME.format(wrap) + (overnight ? " the next morning" : "") + ", including setting up and packing away";
    }

    private static boolean present(String text) {
        return text != null && !text.isBlank();
    }

    // --- layout ---------------------------------------------------------------------------------

    private float width() {
        return PDRectangle.A4.getWidth() - 2 * MARGIN;
    }

    private void newPage() throws IOException {
        if (page != null) {
            page.close();
        }
        PDPage next = new PDPage(PDRectangle.A4);
        document.addPage(next);
        page = new PDPageContentStream(document, next);
        y = PDRectangle.A4.getHeight() - MARGIN;
    }

    private void room(float needed) throws IOException {
        if (y - needed < MARGIN) {
            newPage();
        }
    }

    private void gap(float points) {
        y -= points;
    }

    private void heading(String title) throws IOException {
        room(48);
        gap(10);
        text(title, bold, 12, INK);
        gap(2);
    }

    private void paragraph(String text) throws IOException {
        for (String line : PdfText.wrap(text, regular, 11, width())) {
            text(line, regular, 11, INK);
        }
        gap(6);
    }

    private void line(String text) throws IOException {
        line(text, regular);
    }

    private void line(String text, PDFont font) throws IOException {
        for (String line : PdfText.wrap(text, font, 11, width())) {
            text(line, font, 11, INK);
        }
    }

    private void bullet(String text) throws IOException {
        List<String> lines = PdfText.wrap(text, regular, 11, width() - 16);
        for (int i = 0; i < lines.size(); i++) {
            room(16);
            if (i == 0) {
                page.setNonStrokingColor(BRAND);
                page.addRect(MARGIN + 2, y - 8, 4, 4);
                page.fill();
            }
            page.beginText();
            page.setNonStrokingColor(INK);
            page.setFont(regular, 11);
            page.newLineAtOffset(MARGIN + 16, y - 11);
            page.showText(lines.get(i));
            page.endText();
            y -= 16;
        }
        y -= 3;
    }

    /** One line at the cursor, which moves down past it. */
    private void text(String line, PDFont font, float size, Color colour) throws IOException {
        room(size * 1.45f);
        page.beginText();
        page.setNonStrokingColor(colour);
        page.setFont(font, size);
        page.newLineAtOffset(MARGIN, y - size);
        page.showText(PdfText.safe(line, font));
        page.endText();
        y -= size * 1.45f;
    }
}
