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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes a location release as an A4 PDF: a clearly labelled template, filled in from {@link AgreementFacts}, with
 * blanks for what is not known and a footer on every page saying it is not legal advice.
 */
public final class AgreementPdf {

    public static final String FOOTER = "Template only — not legal advice. Have it reviewed before signing.";

    static final String BLANK = "________________________";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);
    private static final float MARGIN = 56;
    private static final float FOOTER_SPACE = 48;
    private static final Color INK = new Color(17, 24, 39);
    private static final Color MUTED = new Color(90, 98, 112);

    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDDocument document = new PDDocument();
    private PDPageContentStream page;
    private float y;

    private AgreementPdf() {
    }

    /** The agreement as PDF bytes. */
    public static byte[] write(AgreementFacts facts) {
        AgreementPdf pdf = new AgreementPdf();
        try (PDDocument document = pdf.document) {
            pdf.body(facts);
            pdf.page.close();
            pdf.footers(facts);
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle("Location release: " + pdf.safe(facts.venueName(), pdf.regular));
            info.setCreator("CineScout");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the agreement", e);
        }
    }

    private void body(AgreementFacts f) throws IOException {
        newPage();
        text("LOCATION RELEASE", bold, 20, INK);
        text("TEMPLATE  ·  version " + f.version() + "  ·  prepared " + DAY.format(f.preparedOn())
                + (f.preparedBy() == null ? "" : " by " + f.preparedBy()), regular, 9, MUTED);
        gap(10);
        paragraph("This agreement is between the production and the owner or manager of the location named below. Fill in "
                + "the blanks, check every detail with the owner, and have it reviewed before anyone signs.", regular, 10, MUTED);

        heading("1. The production");
        field("Production", f.production());
        field("Production company", f.productionCompany());
        field("Contact for the production", f.preparedBy());

        heading("2. The location");
        field("Venue", f.venueName());
        field("Address", f.venueAddress());
        field("Owner or manager", f.contactName());
        field("Email", f.contactEmail());
        field("Phone", f.contactPhone());

        heading("3. Dates and access");
        field("Shoot dates", dates(f.shootStart(), f.shootEnd()));
        field("Access each day", access(f.callTime(), f.wrapTime()));
        field("Preparation and strike days", null);

        heading("4. People on site");
        field("Cast and crew", f.crewSize() == null ? null : "About " + f.crewSize() + " (an estimate)");
        field("Vehicles and parking", null);

        heading("5. Fee");
        field("Location fee", f.quote());
        field("Payment terms", null);

        heading("6. Terms");
        clause("a", "The owner allows the production to enter the location on the dates and at the times above with its cast, "
                + "crew, vehicles and equipment, to film, photograph and record the location, and to show it as any place, real "
                + "or fictional.");
        clause("b", "The production owns everything it films and records there and may use it in any media, worldwide, "
                + "without further payment beyond the fee above.");
        clause("c", "The production will take reasonable care of the location, leave it as it found it (normal wear "
                + "excepted), and repair or pay for any damage it causes.");
        clause("d", "The production will hold public liability insurance of at least " + BLANK + " for the shoot, and "
                + "show the certificate on request.");
        clause("e", "The owner confirms they have the right to grant this permission and that no one else's consent is "
                + "needed, or names who must also agree: " + BLANK + ".");
        clause("f", "If the shoot moves or is cancelled, the production will tell the owner as soon as it can. "
                + "Cancellation terms: " + BLANK + ".");

        heading("7. Signatures");
        signature("For the production");
        signature("For the owner");
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
        room(60);
        gap(12);
        text(title, bold, 12, INK);
        gap(2);
    }

    private void field(String label, String value) throws IOException {
        boolean known = value != null && !value.isBlank();
        room(18);
        String shown = safe(label + ": ", bold);
        float labelWidth = width(shown, bold, 10);
        page.beginText();
        page.setNonStrokingColor(INK);
        page.setFont(bold, 10);
        page.newLineAtOffset(MARGIN, y - 10);
        page.showText(shown);
        page.endText();
        List<String> lines = wrap(known ? value.strip() : BLANK, regular, 10, width() - labelWidth);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                room(14);
            }
            page.beginText();
            page.setNonStrokingColor(known ? INK : MUTED);
            page.setFont(regular, 10);
            page.newLineAtOffset(MARGIN + labelWidth, y - 10);
            page.showText(lines.get(i));
            page.endText();
            y -= 14;
        }
        y -= 2;
    }

    private void clause(String letter, String text) throws IOException {
        List<String> lines = wrap(text, regular, 10, width() - 18);
        room(14 * Math.min(lines.size(), 3));
        page.beginText();
        page.setNonStrokingColor(INK);
        page.setFont(bold, 10);
        page.newLineAtOffset(MARGIN, y - 10);
        page.showText(letter + ".");
        page.endText();
        for (String line : lines) {
            room(14);
            page.beginText();
            page.setFont(regular, 10);
            page.newLineAtOffset(MARGIN + 18, y - 10);
            page.showText(line);
            page.endText();
            y -= 14;
        }
        y -= 4;
    }

    private void signature(String party) throws IOException {
        room(90);
        gap(6);
        text(party, bold, 10, INK);
        for (String line : new String[] {"Name", "Signature", "Date"}) {
            gap(8);
            text(line + ":  " + BLANK + BLANK, regular, 10, MUTED);
        }
        gap(6);
    }

    private void paragraph(String text, PDFont font, float size, Color colour) throws IOException {
        for (String line : wrap(text, font, size, width())) {
            text(line, font, size, colour);
        }
    }

    /** One line at the cursor, which moves down past it. */
    private void text(String line, PDFont font, float size, Color colour) throws IOException {
        room(size * 1.45f);
        page.beginText();
        page.setNonStrokingColor(colour);
        page.setFont(font, size);
        page.newLineAtOffset(MARGIN, y - size);
        page.showText(safe(line, font));
        page.endText();
        y -= size * 1.45f;
    }

    private void footers(AgreementFacts facts) throws IOException {
        int pages = document.getNumberOfPages();
        for (int i = 0; i < pages; i++) {
            try (PDPageContentStream footer = new PDPageContentStream(document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true)) {
                footer.setStrokingColor(MUTED);
                footer.moveTo(MARGIN, MARGIN + 22);
                footer.lineTo(PDRectangle.A4.getWidth() - MARGIN, MARGIN + 22);
                footer.stroke();
                footer.beginText();
                footer.setNonStrokingColor(INK);
                footer.setFont(bold, 9);
                footer.newLineAtOffset(MARGIN, MARGIN + 8);
                footer.showText(safe(FOOTER, bold));
                footer.endText();
                String right = safe("Version " + facts.version() + "  ·  page " + (i + 1) + " of " + pages, regular);
                footer.beginText();
                footer.setNonStrokingColor(MUTED);
                footer.setFont(regular, 8);
                footer.newLineAtOffset(PDRectangle.A4.getWidth() - MARGIN - width(right, regular, 8), MARGIN - 4);
                footer.showText(right);
                footer.endText();
            }
        }
    }

    // --- text -----------------------------------------------------------------------------------

    /** Words laid into lines no wider than {@code max}; a word longer than a line is cut. Never empty. */
    List<String> wrap(String text, PDFont font, float size, float max) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : safe(text, font).split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                while (width(word, font, size) > max) {
                    int cut = word.length() - 1;
                    while (cut > 1 && width(word.substring(0, cut), font, size) > max) {
                        cut--;
                    }
                    if (!line.isEmpty()) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    lines.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && width(candidate, font, size) > max) {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static float width(String text, PDFont font, float size) throws IOException {
        return font.getStringWidth(text) / 1000 * size;
    }

    /** The text with what the standard fonts cannot show replaced by "?", and tabs and returns by spaces. */
    String safe(String text, PDFont font) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(point -> {
            if (point == '\n') {
                out.append('\n');
            } else if (Character.isWhitespace(point) || Character.isISOControl(point)) {
                out.append(' ');
            } else {
                String character = new String(Character.toChars(point));
                try {
                    font.encode(character);
                    out.append(character);
                } catch (IOException | IllegalArgumentException e) {
                    out.append('?');
                }
            }
        });
        return out.toString();
    }

    public static String dates(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return null;
        }
        if (start == null || end == null || start.equals(end)) {
            return DAY.format(start == null ? end : start);
        }
        return DAY.format(start) + " to " + DAY.format(end);
    }

    public static String access(LocalTime call, LocalTime wrap) {
        if (call == null && wrap == null) {
            return null;
        }
        String from = call == null ? "____" : call.toString();
        String until = wrap == null ? "____" : wrap.toString();
        boolean overnight = call != null && wrap != null && !wrap.isAfter(call);
        return "from " + from + " to " + until + (overnight ? " (the next morning)" : "");
    }
}
