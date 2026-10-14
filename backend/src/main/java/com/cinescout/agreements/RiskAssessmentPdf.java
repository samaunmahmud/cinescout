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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Writes a draft risk assessment as an A4 PDF: the shoot's details, emergency information, the hazards
 * {@link RiskRules} picked with why and suggested controls, room for more found on the walk-round, and sign-off. Every
 * page says it is a draft to be completed on site.
 */
public final class RiskAssessmentPdf {

    public static final String FOOTER = "Draft from recorded details, not a completed assessment. A competent person must check it on site.";

    private static final String BLANK = AgreementPdf.BLANK;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final float MARGIN = 52;
    private static final float FOOTER_SPACE = 44;
    private static final Color INK = new Color(17, 24, 39);
    private static final Color MUTED = new Color(90, 98, 112);
    private static final Color BRAND = new Color(209, 58, 33);
    private static final Color LINE = new Color(214, 218, 225);

    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDDocument document = new PDDocument();
    private PDPageContentStream page;
    private float y;

    private RiskAssessmentPdf() {
    }

    public static byte[] write(RiskFacts facts, List<Hazard> hazards) {
        RiskAssessmentPdf pdf = new RiskAssessmentPdf();
        try (PDDocument document = pdf.document) {
            pdf.body(facts, hazards);
            pdf.page.close();
            pdf.footers();
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle("Risk assessment: " + PdfText.safe(facts.venueName(), pdf.regular));
            info.setCreator("CineScout");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the risk assessment", e);
        }
    }

    private void body(RiskFacts f, List<Hazard> hazards) throws IOException {
        newPage();
        text("RISK ASSESSMENT", bold, 20, INK);
        text("DRAFT  ·  " + f.production() + "  ·  prepared " + DAY.format(f.preparedOn())
                + (f.preparedBy() == null ? "" : " by " + f.preparedBy()), regular, 9, MUTED);
        gap(8);
        paragraph("A starting point from what CineScout has recorded about this venue and scene. Walk the location, add what is "
                + "missing, rate each risk, and strike out what does not apply before the shoot.", regular, 10, MUTED);

        heading("The shoot");
        field("Venue", f.venueName());
        field("Address", f.venueAddress());
        field("Scene", f.sceneHeading());
        field("Dates", AgreementPdf.dates(f.shootStart(), f.shootEnd()));
        field("Hours", AgreementPdf.access(f.callTime(), f.wrapTime()));
        field("Cast and crew", f.crewSize() == null ? null : "About " + f.crewSize());

        heading("In an emergency");
        field("Nearest hospital", f.nearestHospital());
        field("Emergency number", null);
        field("First aider", null);
        field("Fire assembly point", null);
        field("Phone signal", signal(f.phoneSignal()));

        heading("Hazards");
        int number = 1;
        for (Hazard hazard : hazards) {
            hazard(number++, hazard);
        }

        heading("Other hazards found on the walk-round");
        for (int i = 0; i < 3; i++) {
            gap(6);
            text(BLANK + BLANK + BLANK, regular, 10, MUTED);
        }
        if (f.recceNotes() != null && !f.recceNotes().isBlank()) {
            heading("Recce notes");
            paragraph(f.recceNotes().strip(), regular, 10, INK);
        }

        heading("Sign-off");
        for (String who : new String[] {"Assessed by", "Reviewed by"}) {
            room(30);
            gap(6);
            text(who + ":  " + BLANK + "   Signature: " + BLANK + "   Date: ________", regular, 10, MUTED);
        }
    }

    private void hazard(int number, Hazard hazard) throws IOException {
        List<String> why = PdfText.wrap("Why: " + hazard.why(), regular, 9.5f, width() - 18);
        int controlLines = 0;
        for (String control : hazard.controls()) {
            controlLines += PdfText.wrap(control, regular, 9.5f, width() - 30).size();
        }
        // The whole hazard on one page: its rating line belongs with it.
        room(10 + 16 + 13 * (why.size() + controlLines) + 15);
        gap(6);
        page.setStrokingColor(LINE);
        page.moveTo(MARGIN, y);
        page.lineTo(MARGIN + width(), y);
        page.stroke();
        gap(4);
        text(number + ". " + hazard.title(), bold, 11, INK);
        for (String line : why) {
            room(13);
            write(line, regular, 9.5f, MUTED, MARGIN + 18);
            y -= 13;
        }
        for (String control : hazard.controls()) {
            List<String> lines = PdfText.wrap(control, regular, 9.5f, width() - 30);
            for (int i = 0; i < lines.size(); i++) {
                room(13);
                if (i == 0) {
                    page.setNonStrokingColor(BRAND);
                    page.addRect(MARGIN + 20, y - 7, 3.5f, 3.5f);
                    page.fill();
                }
                write(lines.get(i), regular, 9.5f, INK, MARGIN + 30);
                y -= 13;
            }
        }
        room(16);
        write("Risk before:  L  /  M  /  H        Risk after:  L  /  M  /  H        Who:  " + BLANK, regular, 9, MUTED, MARGIN + 18);
        y -= 15;
    }

    private static String signal(String phoneSignal) {
        if (phoneSignal == null) {
            return null;
        }
        return switch (phoneSignal) {
            case "NONE" -> "None on the recce: plan radios or a landline";
            case "POOR" -> "Poor on the recce";
            case "OK" -> "OK on the recce";
            case "STRONG" -> "Strong on the recce";
            default -> null;
        };
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
        if (y - needed < MARGIN + FOOTER_SPACE) {
            newPage();
        }
    }

    private void gap(float points) {
        y -= points;
    }

    private void heading(String title) throws IOException {
        room(56);
        gap(12);
        text(title, bold, 12, BRAND);
        gap(2);
    }

    private void field(String label, String value) throws IOException {
        boolean known = value != null && !value.isBlank();
        room(16);
        String shown = PdfText.safe(label + ": ", bold);
        float labelWidth = PdfText.width(shown, bold, 10);
        write(shown, bold, 10, INK, MARGIN);
        List<String> lines = PdfText.wrap(known ? value.strip() : BLANK, regular, 10, width() - labelWidth);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                room(14);
            }
            write(lines.get(i), regular, 10, known ? INK : MUTED, MARGIN + labelWidth);
            y -= 14;
        }
    }

    private void paragraph(String text, PDFont font, float size, Color colour) throws IOException {
        for (String line : PdfText.wrap(text, font, size, width())) {
            text(line, font, size, colour);
        }
    }

    private void text(String line, PDFont font, float size, Color colour) throws IOException {
        room(size * 1.45f);
        write(PdfText.safe(line, font), font, size, colour, MARGIN);
        y -= size * 1.45f;
    }

    /** One already-safe line with its top at the cursor; the cursor stays put. */
    private void write(String line, PDFont font, float size, Color colour, float x) throws IOException {
        page.beginText();
        page.setNonStrokingColor(colour);
        page.setFont(font, size);
        page.newLineAtOffset(x, y - size);
        page.showText(PdfText.safe(line, font));
        page.endText();
    }

    private void footers() throws IOException {
        int pages = document.getNumberOfPages();
        for (int i = 0; i < pages; i++) {
            try (PDPageContentStream footer = new PDPageContentStream(document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true)) {
                footer.setStrokingColor(MUTED);
                footer.moveTo(MARGIN, MARGIN + 20);
                footer.lineTo(PDRectangle.A4.getWidth() - MARGIN, MARGIN + 20);
                footer.stroke();
                footer.beginText();
                footer.setNonStrokingColor(INK);
                footer.setFont(bold, 8.5f);
                footer.newLineAtOffset(MARGIN, MARGIN + 7);
                footer.showText(PdfText.safe(FOOTER, bold));
                footer.endText();
                String right = "Page " + (i + 1) + " of " + pages;
                footer.beginText();
                footer.setNonStrokingColor(MUTED);
                footer.setFont(regular, 8);
                footer.newLineAtOffset(PDRectangle.A4.getWidth() - MARGIN - PdfText.width(right, regular, 8), MARGIN - 5);
                footer.showText(right);
                footer.endText();
            }
        }
    }
}
