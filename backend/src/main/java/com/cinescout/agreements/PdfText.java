package com.cinescout.agreements;

import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Text for the standard PDF fonts: what they cannot show replaced, and words laid into lines. */
final class PdfText {

    private PdfText() {
    }

    /** Words laid into lines no wider than {@code max}; a word longer than a line is cut. Never empty. */
    static List<String> wrap(String text, PDFont font, float size, float max) throws IOException {
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

    static float width(String text, PDFont font, float size) throws IOException {
        return font.getStringWidth(text) / 1000 * size;
    }

    /** The text with what the font cannot show replaced by "?", and tabs and returns by spaces. */
    static String safe(String text, PDFont font) {
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
