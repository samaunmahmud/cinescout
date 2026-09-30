package com.cinescout.export;

import java.util.List;

/**
 * Writes a table as CSV (RFC 4180) that spreadsheets open as intended: a byte order mark so Excel reads it as
 * UTF-8, and cells that a spreadsheet would run as a formula defused. Venue names and notes come from web
 * pages and users, so a cell starting with {@code =}, {@code +}, {@code -} or {@code @} is prefixed with an
 * apostrophe, which makes it plain text (unless it is just a number).
 */
public final class Csv {

    private static final String BYTE_ORDER_MARK = "﻿";
    private static final String LINE_END = "\r\n";

    private Csv() {
    }

    /** A null cell is written as an empty one. */
    public static String of(List<String> header, List<List<String>> rows) {
        StringBuilder out = new StringBuilder(BYTE_ORDER_MARK);
        line(out, header);
        rows.forEach(row -> line(out, row));
        return out.toString();
    }

    private static void line(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells.get(i)));
        }
        out.append(LINE_END);
    }

    static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        // A plain number such as a longitude of -73.96 is what it looks like, and stays a number.
        boolean formula = "=+-@\t\r".indexOf(value.charAt(0)) >= 0 && !value.matches("[+-]?\\d+(\\.\\d+)?");
        String text = formula ? "'" + value : value;
        boolean quote = text.chars().anyMatch(c -> c == '"' || c == ',' || c == '\n' || c == '\r')
                || Character.isWhitespace(text.charAt(0)) || Character.isWhitespace(text.charAt(text.length() - 1));
        return quote ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }
}
