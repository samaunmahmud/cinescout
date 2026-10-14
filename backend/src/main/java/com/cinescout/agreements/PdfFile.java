package com.cinescout.agreements;

import java.util.Locale;

/** A PDF made on request, with the name to save it under. */
public record PdfFile(String filename, byte[] content) {

    /** "{prefix}-starlite-diner{suffix}.pdf", or "{prefix}-venue{suffix}.pdf" when the name has no plain letters. */
    static String named(String prefix, String venue, String suffix) {
        String slug = venue == null ? "" : venue.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-$", "");
        }
        return prefix + "-" + (slug.isEmpty() ? "venue" : slug) + suffix + ".pdf";
    }
}
