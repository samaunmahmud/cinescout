package com.cinescout.export;

import com.cinescout.domain.Location;
import com.cinescout.domain.Project;
import com.cinescout.domain.Scene;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A project's candidate locations as a spreadsheet, one venue a row, for sharing with people who do not
 * use the app (a producer, a location manager).
 *
 * @param filename safe to put in a Content-Disposition header: lower-case ASCII letters, digits and hyphens
 */
public record LocationExport(String filename, String csv) {

    static final List<String> HEADER = List.of("Scene number", "Scene", "Venue", "Status", "Fit score", "Booking",
            "Address", "Latitude", "Longitude", "Web page", "Why it fits", "Booking note", "Warnings", "Notes");

    private static final int MAX_NAME_LENGTH = 60;

    /** @param locations in the order they should appear, each with its scene loaded */
    public static LocationExport of(Project project, List<Location> locations) {
        return new LocationExport(filename(project.getTitle()), Csv.of(HEADER, locations.stream().map(LocationExport::row).toList()));
    }

    private static List<String> row(Location location) {
        Scene scene = location.getScene();
        List<String> warnings = location.getFootprintWarnings();
        return Arrays.asList(
                text(scene.getSceneNumber()),
                scene.getTitle(),
                location.getName(),
                label(location.getStatus()),
                text(location.getFitScore()),
                label(location.getBookingFriction()),
                location.getAddress(),
                location.getLatitude() == null ? null : location.getLatitude().toPlainString(),
                location.getLongitude() == null ? null : location.getLongitude().toPlainString(),
                location.getSourceUrl(),
                location.getFitReason(),
                location.getFrictionNote(),
                warnings == null ? null : String.join("; ", warnings),
                location.getNotes());
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /** "SHORTLISTED" as "Shortlisted". */
    private static String label(Enum<?> value) {
        if (value == null) {
            return null;
        }
        String name = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** "Café Noir: Part 2" as "cafe-noir-part-2-locations.csv". */
    static String filename(String projectTitle) {
        String ascii = Normalizer.normalize(projectTitle, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_NAME_LENGTH) {
            slug = slug.substring(0, MAX_NAME_LENGTH).replaceAll("-+$", "");
        }
        return (slug.isEmpty() ? "project" : slug) + "-locations.csv";
    }
}
