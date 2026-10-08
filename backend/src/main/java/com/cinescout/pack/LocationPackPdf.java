package com.cinescout.pack;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes a production's location pack as an A4 PDF: a title page, then each scene with its venues (photo, address and
 * map link, contact and fee, the AI's read, booked days, recce answers, permit office), and a footer on every page.
 */
public final class LocationPackPdf {

    private static final Logger log = LoggerFactory.getLogger(LocationPackPdf.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final float MARGIN = 50;
    private static final float FOOTER_SPACE = 40;
    private static final float PHOTO_HEIGHT = 190;
    private static final Color INK = new Color(17, 24, 39);
    private static final Color MUTED = new Color(90, 98, 112);
    private static final Color CUE = new Color(194, 65, 12);

    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDDocument document = new PDDocument();
    private PDPageContentStream page;
    private float y;

    private LocationPackPdf() {
    }

    public static byte[] write(PackFacts facts) {
        LocationPackPdf pdf = new LocationPackPdf();
        try (PDDocument document = pdf.document) {
            pdf.body(facts);
            pdf.page.close();
            pdf.footers(facts);
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle("Location pack: " + pdf.safe(facts.production(), pdf.regular));
            info.setCreator("CineScout");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the location pack", e);
        }
    }

    private void body(PackFacts f) throws IOException {
        newPage();
        gap(120);
        text("LOCATION PACK", bold, 12, CUE);
        gap(6);
        for (String line : wrap(f.production(), bold, 30, width())) {
            text(line, bold, 30, INK);
        }
        if (f.area() != null) {
            text(f.area(), regular, 14, MUTED);
        }
        gap(18);
        long venues = f.scenes().stream().mapToLong(scene -> scene.venues().size()).sum();
        text(f.scenes().size() + (f.scenes().size() == 1 ? " scene" : " scenes") + "  ·  " + venues + (venues == 1 ? " venue" : " venues"),
                regular, 11, INK);
        text("Prepared " + DAY.format(f.preparedOn()) + (f.preparedBy() == null ? "" : " by " + f.preparedBy()), regular, 11, MUTED);
        gap(16);
        paragraph("For each scene: the confirmed venue, or the best shortlisted ones while none is confirmed. Check every detail "
                + "with the venue before relying on it.", regular, 10, MUTED);

        for (PackFacts.Scene scene : f.scenes()) {
            newPage();
            text("SCENE", bold, 9, CUE);
            for (String line : wrap(scene.label(), bold, 18, width())) {
                text(line, bold, 18, INK);
            }
            if (scene.dates() != null || scene.access() != null) {
                text(join("  ·  ", scene.dates(), scene.access()), regular, 10, MUTED);
            }
            gap(6);
            if (scene.venues().isEmpty()) {
                paragraph("No venue confirmed or shortlisted yet.", regular, 10, MUTED);
            }
            for (PackFacts.Venue venue : scene.venues()) {
                venue(venue);
            }
        }
    }

    private void venue(PackFacts.Venue v) throws IOException {
        room(120);
        gap(10);
        rule();
        gap(8);
        text(v.name(), bold, 15, INK);
        text(join("  ·  ", v.status(), v.fitScore() == null ? null : "Fit " + v.fitScore() + "/100"), bold, 9, CUE);
        gap(4);
        photo(v.photo());
        field("Address", v.address());
        field("Position", v.position());
        field("Map", v.mapUrl());
        field("Website", v.website());
        field("Contact", v.contact());
        field("Fee", v.quote());
        field("Booking", join(" — ", v.booking(), v.bookingNote()));
        field("Permit", v.permit());
        field("Why it fits", v.fitReason());
        if (!v.warnings().isEmpty()) {
            field("Watch out for", String.join("; ", v.warnings()));
        }
        field("Notes", v.notes());
        if (!v.days().isEmpty()) {
            field("Days", String.join("\n", v.days()));
        }
        if (!v.recce().isEmpty()) {
            room(40);
            gap(4);
            text("Tech recce", bold, 10, INK);
            for (String[] answer : v.recce()) {
                field(answer[0], answer[1]);
            }
        }
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

    private void rule() throws IOException {
        page.setStrokingColor(new Color(220, 223, 228));
        page.moveTo(MARGIN, y);
        page.lineTo(PDRectangle.A4.getWidth() - MARGIN, y);
        page.stroke();
    }

    /** The photo scaled to the column's width and at most {@link #PHOTO_HEIGHT} high; one that will not decode is left out. */
    private void photo(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        PDImageXObject image;
        try {
            image = PDImageXObject.createFromByteArray(document, bytes, "photo");
        } catch (IOException | IllegalArgumentException e) {
            log.debug("A venue photo could not be put in the location pack: {}", e.getMessage());
            return;
        }
        float scale = Math.min(width() / image.getWidth(), PHOTO_HEIGHT / image.getHeight());
        float w = image.getWidth() * scale;
        float h = image.getHeight() * scale;
        room(h + 10);
        page.drawImage(image, MARGIN, y - h, w, h);
        y -= h + 10;
    }

    private void field(String label, String value) throws IOException {
        if (value == null || value.isBlank()) {
            return;
        }
        float labelWidth = 92;
        List<String> lines = wrap(value.strip(), regular, 10, width() - labelWidth);
        room(14 * Math.min(lines.size(), 2));
        page.beginText();
        page.setNonStrokingColor(MUTED);
        page.setFont(bold, 9);
        page.newLineAtOffset(MARGIN, y - 10);
        page.showText(safe(label, bold));
        page.endText();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                room(14);
            }
            page.beginText();
            page.setNonStrokingColor(INK);
            page.setFont(regular, 10);
            page.newLineAtOffset(MARGIN + labelWidth, y - 10);
            page.showText(lines.get(i));
            page.endText();
            y -= 14;
        }
        y -= 2;
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

    private void footers(PackFacts facts) throws IOException {
        int pages = document.getNumberOfPages();
        for (int i = 0; i < pages; i++) {
            try (PDPageContentStream footer = new PDPageContentStream(document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true)) {
                String left = safe(facts.production() + "  ·  location pack", regular);
                String right = safe("page " + (i + 1) + " of " + pages, regular);
                footer.beginText();
                footer.setNonStrokingColor(MUTED);
                footer.setFont(regular, 8);
                footer.newLineAtOffset(MARGIN, MARGIN - 14);
                footer.showText(left.length() > 90 ? left.substring(0, 90) : left);
                footer.endText();
                footer.beginText();
                footer.setFont(regular, 8);
                footer.newLineAtOffset(PDRectangle.A4.getWidth() - MARGIN - width(right, regular, 8), MARGIN - 14);
                footer.showText(right);
                footer.endText();
            }
        }
    }

    // --- text -----------------------------------------------------------------------------------

    private static String join(String separator, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                kept.add(part.strip());
            }
        }
        return kept.isEmpty() ? null : String.join(separator, kept);
    }

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
}
